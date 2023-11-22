package app;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Queue;
import java.util.ResourceBundle;
import java.util.logging.Level;

import javax.security.auth.login.LoginException;
import jakarta.xml.bind.annotation.adapters.HexBinaryAdapter;

import wiki.Wiki;

interface StatusHandler {

	/**
	 * Handle the status for the initial step of each iteration
	 * 
	 * @param i
	 *            the current index of the file about to download
	 * @param fileName
	 *            the name of the file about to download
	 */
	void handle(int i, String fileName);

	/**
	 * Handle the status for the last step of the file download
	 * 
	 * @param status
	 *            Can be something like "... saved" or "... exists"
	 */
	void handleConclusion(String status);
}

public abstract class ImkerBase extends App {
	protected static final String VERSION = "v16.09.13";
	protected static final String PROGRAM_NAME = "Imker";
	protected static final String RESOURCE_BUNDLE_BASE_NAME = "i18n/Bundle";
	protected static final String GITHUB_ISSUE_TRACKER = "https://github.com/MarcoFalke/wiki-java-tools/issues/new?title=%s&body=%s";
	private static final String[] INVALID_FILE_NAME_CHARS = { "{", "}", "<",
			">", "[", "]", "|" };
	private static final String[] INVALID_WINDOWS_CHARS = { "?", "\"", "*" };
	private static final String FOLDER_WINDOWS_ENCODED_FILES = "invalid_names_encoded";
	private Wiki wiki;
	private File outputFolder;
	// Variables only valid for one round; Need invalidation {
	private String[] fileNames;
	private FileStatus[] fileStatuses;
	private boolean windowsEncodeSubfolder = false;
	private int filePrefixLength;
	// }
	// Preferences {
	protected static final String PREF_WIKI_DOMAIN_DEFAULT = "commons.wikimedia.org";
	// }
	protected static final ResourceBundle MSGS = ResourceBundle.getBundle(
			RESOURCE_BUNDLE_BASE_NAME, Locale.getDefault());

	protected enum FileStatus {
		DOWNLOADED, NOT_FOUND, CHECKSUM_CONFIRMED, CHECKSUM_ERROR
	}

	/**
	 * Read the given file and extract valid file names in each line
	 * 
	 * @param localFilePath
	 *            the path to the local file
	 * @return array holding all file names
	 * @throws FileNotFoundException
	 *             if the local file was not found
	 * @throws IOException
	 *             if there was an issue reading the file
	 */
	protected String[] parseFileNames(String localFilePath)
			throws FileNotFoundException, IOException {
		// TODO resolve redirects
		Queue<String> FileNameQueue = new LinkedList<String>();

		try (BufferedReader br = new BufferedReader(new FileReader(
				localFilePath))) {
			String line = br.readLine();
			while (line != null) {
				String fileName = normalizeFileName(line);
				if (fileName != null)
					FileNameQueue.add(fileName);
				line = br.readLine();
			}
		}
		String[] tempStringArray = { "" };
		return FileNameQueue.toArray(tempStringArray);
	}

	/**
	 * Loop over all file names and call the StatusHandler; Files already
	 * existing locally as well as files not found in the remote are skipped!
	 * 
	 * @param sh
	 *            the status handler to call
	 * @throws IOException
	 *             if an io error (network or file related) occurs
	 * @throws LoginException
	 */
	protected void downloadLoop(StatusHandler sh) throws IOException,
			LoginException {
		if (windowsEncodeSubfolder)
			new File(outputFolder.getPath() + File.separator + FOLDER_WINDOWS_ENCODED_FILES).mkdir();

		for (int i = 0; i < fileNames.length; i++) {
			final String fileName = fileNames[i]
					.substring(getFilePrefixLenght());
			sh.handle(i, fileName);

			final File outputFile = new File(outputFolder.getPath()
					+ File.separator + windowsNormalize(fileName));
			if (outputFile.exists()) {
				fileStatuses[i] = FileStatus.DOWNLOADED;
				sh.handleConclusion(" ... "
						+ MSGS.getString("Status_File_Exists"));
				continue;
			}
			// TODO add option to rate limit
			boolean downloaded = (boolean) attemptFetch(new WikiAPI() {

				@Override
				public Object fetch() throws IOException {
					return wiki.getImage(fileName, outputFile);
				}
			}, MAX_FAILS, MAX_EXCEPTION_SLEEP_TIME);
			if (downloaded == false) {
				fileStatuses[i] = FileStatus.NOT_FOUND;
				sh.handleConclusion(" ... "
						+ MSGS.getString("Status_File_Not_Found"));
				continue;
			}
			fileStatuses[i] = FileStatus.DOWNLOADED;
			sh.handleConclusion(" ... " + MSGS.getString("Status_File_Saved"));
		}
	}

	/**
	 * Returns true if there are invalid Windows characters in at least one file
	 * name and the operating system is windows
	 * 
	 * @return if affected by the "windows bug"
	 */
	protected boolean checkWindowsBug() {
		if (!System.getProperty("os.name").startsWith("Windows"))
			return false;
		for (String filename : fileNames) {
			if (checkWindowsBug(filename)) {
				windowsEncodeSubfolder = true;
				return true;
			}
		}
		return false;
	}

	/**
	 * Returns true if there are invalid Windows characters in at least one file
	 * name and the operating system is windows
	 *
	 * @param fileName
	 * @return if affected by the "windows bug"
	 */
	protected boolean checkWindowsBug(String fileName) {
		for (String invalidChar : INVALID_WINDOWS_CHARS)
			if (fileName.contains(invalidChar)) {
				return true;
			}
		return false;
	}

	/**
	 * Loop over all file names and call the StatusHandler; All files with
	 * FileStatus.DOWNLOADED get their checksum confirmed
	 * 
	 * @param sh
	 *            the status handler to call
	 * @throws IOException
	 *             if an io error (network or file related) occurs
	 * @throws NoSuchAlgorithmException
	 *             should never happen
	 * @throws FileNotFoundException
	 *             if the file does not exist
	 */
	protected int checksumLoop(StatusHandler sh) throws FileNotFoundException,
			NoSuchAlgorithmException, IOException {
		int errors = 0;
		for (int i = 0; i < fileStatuses.length; i++) {
			final String fileName = fileNames[i]
					.substring(getFilePrefixLenght());
			sh.handle(i, fileName);
			if (fileStatuses[i] == FileStatus.DOWNLOADED) {

				String localSHA1 = calcSHA1(new File(outputFolder.getPath()
						+ File.separator + windowsNormalize(fileName)));
				String wikiSHA1 = ((String) wiki.getFileMetadata(fileName).get(
						"sha1")).toUpperCase();

				if (localSHA1.equals(wikiSHA1)) {
					fileStatuses[i] = FileStatus.CHECKSUM_CONFIRMED;
					sh.handleConclusion(" ... "
							+ MSGS.getString("Status_Checksum_Confirmed"));
				} else {
					fileStatuses[i] = FileStatus.CHECKSUM_ERROR;
					errors++;
					sh.handleConclusion(" ... "
							+ MSGS.getString("Status_Checksum_Error"));
				}
			}
		}
		return errors;
	}

	/**
	 * Replace invalid characters in this file name by a valid one
	 * 
	 * @param fileName
	 *            the initial file name
	 * @return the resulting file name
	 */
	private String windowsNormalize(String fileName) {
		if (windowsEncodeSubfolder && checkWindowsBug(fileName))
			try {
				fileName = FOLDER_WINDOWS_ENCODED_FILES + File.separator
						+ URLEncoder.encode(fileName, "UTF-8").replace("*", "%2A");
			} catch (UnsupportedEncodingException e) {
				// never happens
				e.printStackTrace();
				System.exit(-1);
			}
		return fileName;
	}

	/**
	 * Gets the list of images used on a particular page. If resolveRedirect is
	 * false and there are redirects on the page, both the source and target
	 * page get included in the list. If resolveRedirect is true only the target
	 * pages are included and all duplicates are wiped.
	 * 
	 * @param title
	 *            the name of the page
	 * @param resolveRedirects
	 *            resolve redirects
	 * @return the list of images
	 * @throws LoginException
	 * @throws IOException
	 *             if a network error occurs
	 */
	protected String[] getImagesOnPage(final String title,
			final boolean resolveRedirects) throws LoginException, IOException {
		String[] list = (String[]) attemptFetch(new WikiAPI() {

			@Override
			public String[] fetch() throws IOException {
				return wiki.getImagesOnPage(title);
			}
		}, MAX_FAILS, MAX_EXCEPTION_SLEEP_TIME);
		if (!resolveRedirects)
			return list;

		String[] red = wiki.resolveRedirects(list);
		// create HashSet to get rid of duplicates
		HashSet<String> set = new HashSet<String>(list.length);
		for (int i = 0; i < red.length; i++) {
			if (red[i] == null)
				set.add(list[i]);
			else
				set.add(red[i]);
		}
		return set.toArray(new String[] {});
	}

	/**
	 * Read the file and calculate the SHA-1 checksum
	 * 
	 * @param file
	 *            the file to read
	 * @return the hex representation of the SHA-1 using uppercase chars
	 * @throws FileNotFoundException
	 *             if the file does not exist, is a directory rather than a
	 *             regular file, or for some other reason cannot be opened for
	 *             reading
	 * @throws IOException
	 *             if an I/O error occurs
	 * @throws NoSuchAlgorithmException
	 *             should never happen
	 */
	private String calcSHA1(File file) throws FileNotFoundException,
			IOException, NoSuchAlgorithmException {

		MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
		try (InputStream input = new FileInputStream(file)) {

			byte[] buffer = new byte[8192];
			int len = input.read(buffer);

			while (len != -1) {
				sha1.update(buffer, 0, len);
				len = input.read(buffer);
			}

			return new HexBinaryAdapter().marshal(sha1.digest());
		}
	}

	/**
	 * Discard invalid file names and normalize the others.
	 * 
	 * @param line
	 *            the String to be normalized
	 * @return a normalized file name or null
	 */
	protected String normalizeFileName(String line) {
		for (String invalidChar : INVALID_FILE_NAME_CHARS) {
			if (line.contains(invalidChar))
				return null;
		}
		line = "File:" + line.replaceFirst("^([fF]ile:)", "");
		if (line.length() == "File:".length())
			return null;
		return line;
	}

	/**
	 * Reset the current round; Should be called after the download was verified
	 */
	protected void resetMemory() {
		setFileNames(null);
		setFileStatuses(null);
		windowsEncodeSubfolder = false;
		wiki = null;
	}

	/**
	 * Return the current array of file names
	 * 
	 * @return the array
	 */
	protected String[] getFileNames() {
		return fileNames;
	}

	/**
	 * Set the array of file names
	 * 
	 * @param fileNames
	 *            the new array of file names
	 */
	protected void setFileNames(String[] fileNames) {
		this.fileNames = fileNames;
	}

	/**
	 * Return the array of file statuses
	 * 
	 * @return the array
	 */
	protected FileStatus[] getFileStatuses() {
		return fileStatuses;
	}

	/**
	 * Set the array of file statuses
	 * 
	 * @param fileStatuses
	 *            the new array of file statuses
	 */
	protected void setFileStatuses(FileStatus[] fileStatuses) {
		this.fileStatuses = fileStatuses;
	}

	/**
	 * Return the output folder
	 * 
	 * @return the folder
	 */
	public File getOutputFolder() {
		return outputFolder;
	}

	/**
	 * Set the output folder if the argument is not null; Do nothing if the
	 * argument is null or not a directory
	 * 
	 * @param outputFolder
	 *            a folder or null
	 */
	public void setOutputFolder(File outputFolder) {
		if (outputFolder == null || !outputFolder.isDirectory())
			return;
		this.outputFolder = outputFolder;
	}

	/**
	 * Return the current wiki
	 * 
	 * @return the wiki
	 */
	public Wiki getWiki() {
		return wiki;
	}

	/**
	 * Set the wiki to the wiki represented by the given domain name
	 * 
	 * @param domain
	 *            the wiki domain name e.g. en.wikipedia.org
	 * @throws IOException
	 *             when a network error occurs
	 */
	public void setWiki(String domain) throws IOException {
		wiki = new Wiki(domain);
		wiki.setMaxLag(3);
		wiki.setLogLevel(Level.WARNING);
		filePrefixLength = 1 + wiki.namespaceIdentifier(Wiki.FILE_NAMESPACE).length();
	}

	public int getFilePrefixLenght() {
		return filePrefixLength;
	}
}
