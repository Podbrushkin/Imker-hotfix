package app;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.security.NoSuchAlgorithmException;
import javax.security.auth.login.LoginException;

import wiki.Wiki;

@Parameters(separators = "=", resourceBundle = ImkerBase.RESOURCE_BUNDLE_BASE_NAME)
class Preferences {
	protected final String NAME_CATEGORY = "--category";
	@Parameter(names = { NAME_CATEGORY, "-c" }, descriptionKey = "Description_Category")
	protected String category = null;

	protected final String NAME_PAGE = "--page";
	@Parameter(names = { NAME_PAGE, "-p" }, descriptionKey = "Description_Page")
	protected String page = null;

	protected final String NAME_FILE = "--file";
	@Parameter(names = { NAME_FILE, "-f" }, descriptionKey = "Description_File")
	protected String file = null;

	@Parameter(names = { "--outfolder", "-o" }, descriptionKey = "Description_Output_Folder", required = true)
	protected String outfolder = null;

	@Parameter(names = { "--help", "-?", "-h" }, hidden = true, help = true)
	protected boolean help = false;

	@Parameter(names = { "--domain", "-d" }, descriptionKey = "Description_Wiki_Domain")
	protected String wikiDomain = ImkerBase.PREF_WIKI_DOMAIN_DEFAULT;
}

public class ImkerCLI extends ImkerBase {
	private final StatusHandler stdOutPrint = new StatusHandler() {

		@Override
		public void handle(int i, String fileName) {
			System.out.println("(" + (i + 1) + "/" + getFileNames().length
					+ "): " + fileName);
		}

		@Override
		public void handleConclusion(String status2) {
			System.out.println(status2);
		}
	};

	public static void main(String[] args) throws FileNotFoundException,
			IOException, LoginException, NoSuchAlgorithmException {

		final ImkerCLI cli = new ImkerCLI();

		System.out.println(PROGRAM_NAME);
		System.out.println(MSGS.getString("Description_Program"));
		System.out.println(VERSION);
		System.out.println();

		cli.readAndSetArgs(args, cli);

		cli.solveWindowsBug();
		cli.download();
		cli.verifyChecksum();

		cli.resetMemory();
	}

	/**
	 * Verify the checksum of all files and request permission from the user to
	 * delete corrupt files
	 * 
	 * @throws NoSuchAlgorithmException
	 * @throws IOException
	 */
	private void verifyChecksum() throws NoSuchAlgorithmException, IOException {
		System.out.println(MSGS.getString("Status_Checksum"));
		int errors = checksumLoop(stdOutPrint);
		System.out.println(String.format(
				MSGS.getString("Status_Checksum_Complete"), errors));
		if (errors == 0) {
			return;
		}

		for (int i = 0; i < getFileStatuses().length; i++) {
			if (getFileStatuses()[i] == FileStatus.CHECKSUM_ERROR) {
				System.out.println(" * " + getFileNames()[i]);
			}
		}
		System.out.println(String.format(MSGS.getString("Hint_Files_Corrupt"),
				errors));

		promptEnter();

		for (int i = 0; i < getFileStatuses().length; i++) {
			if (getFileStatuses()[i] == FileStatus.CHECKSUM_ERROR)
				Files.delete(new File(getOutputFolder().getPath()
						+ File.separator
						+ getFileNames()[i].substring(getFilePrefixLenght()))
						.toPath());
		}

		System.out.println(MSGS.getString("Status_Checksum_Deleted"));
		System.out.println(MSGS.getString("Status_Restart_Needed"));
	}

	/**
	 * Read the command line arguments and try to set options and preferences.
	 * 
	 * @throws IOException
	 *             if an IO issue occurs (network or file related)
	 * @throws LoginException
	 */
	protected void readAndSetArgs(String[] args, ImkerCLI cli) throws LoginException, IOException {
		Preferences prefs = new Preferences();
		JCommander jc = new JCommander(prefs, args);
		jc.setProgramName("java -jar imker-cli.jar");

		if (prefs.help) {
			printHelp(jc, prefs);
			System.exit(0);
		}

		// Optional Args {
		cli.setWiki(prefs.wikiDomain);
		// }

		// Required Args {
		cli.setFolder(prefs.outfolder);
		if (!cli.setFilenames(prefs.category, prefs.page, prefs.file)) {
			printHelp(jc, prefs);
			System.exit(-1);
		}
		// }
	}

	/**
	 * Check if affected by the "invalid character windows bug" and ask the user
	 * to exit or replace those chars
	 * 
	 * @throws IOException
	 *             if an I/O error occurs
	 */
	private void solveWindowsBug() throws IOException {
		if (!checkWindowsBug())
			return;

		System.out.println(MSGS.getString("Hint_Windows_Bug"));
		promptEnter();
	}

	/**
	 * Attempts download of the previously fetched file names
	 * 
	 * @throws IOException
	 *             if a network error occurs
	 * @throws LoginException
	 */
	private void download() throws IOException, LoginException {

		System.out.println("\n"
				+ MSGS.getString("Text_Folder")
				+ " "
				+ getOutputFolder().getPath()
				+ "\n"
				+ String.format(MSGS.getString("Prompt_Download"),
						getFileNames().length));
		promptEnter();

		downloadLoop(stdOutPrint);

		System.out.println("\n" + MSGS.getString("Status_Download_Complete"));
	}

	/**
	 * Prompt the user to hit enter and wait for them to hit enter
	 * 
	 * @throws IOException
	 *             if an I/O error occurs
	 * 
	 */
	private void promptEnter() throws IOException {
		System.out.println(MSGS.getString("Prompt_Enter"));
		System.in.read();
	}

	/**
	 * Parse the given path parameter from the command line or exit if no such
	 * directory is found
	 * 
	 * @param folder
	 *            the folder read from the command line
	 */
	private void setFolder(String path) {
		File folder = new File(path);
		if (folder.isDirectory()) {
			setOutputFolder(folder);
		} else {
			System.out.println(MSGS.getString("Status_Not_A_Folder") + " " + path);
			System.exit(-1);
		}
	}

	/**
	 * Search for a category, a page or a file in the given parameter in this
	 * order or exit if none was found
	 * 
	 * @param category
	 * @param file
	 * @param page
	 * @throws FileNotFoundException
	 *             if the file parameter points to a missing file
	 * @throws IOException
	 *             if a IO issue occurs (network or file related)
	 * @throws LoginException
	 */
	private boolean setFilenames(final String category, String page, String file)
			throws FileNotFoundException, IOException, LoginException {

		String[] fnames = null;
		if (category != null) {
			fnames = (String[]) attemptFetch(new WikiAPI() {

				@Override
				public String[] fetch() throws IOException {
					boolean subcat = false; // TODO: add argument --subcat
					return getWiki().getCategoryMembers(category, subcat, Wiki.FILE_NAMESPACE);
				}
			}, MAX_FAILS, MAX_EXCEPTION_SLEEP_TIME);
		} else if (page != null) {
			fnames = getImagesOnPage(page, true);
		} else if (file != null) {
			fnames = readFileNames(file);
		} else {
			return false;
		}
		setFileStatuses(new FileStatus[fnames.length]);
		setFileNames(fnames);
		return true;
	}

	/**
	 * Create a new file from the path and return valid file names in it or exit
	 * if it is a folder
	 * 
	 * @param localFilePath
	 *            the path to the local file
	 * @return a array of Strings holding all file names
	 * @throws FileNotFoundException
	 *             if the file can not be found
	 * @throws IOException
	 *             if there was an issue reading the file
	 */
	private String[] readFileNames(String localFilePath)
			throws FileNotFoundException, IOException {
		File input = new File(localFilePath);
		if (input.isFile()) {
			return parseFileNames(localFilePath);
		} else {
			System.out.println(MSGS.getString("Status_Not_A_File") + " "
					+ localFilePath);
			System.exit(-1);
			return null;
		}
	}

	/**
	 * Print help message
	 */
	private void printHelp(JCommander jc, Preferences prefs) {
		jc.usage();
		System.out.println(MSGS.getString("Description_Download_Source"));

		// \u21B3 is unicode for ↳
		System.out.println(String.format(" \u21B3 %s (%s)",
				MSGS.getString("Text_Wiki_Cat"),
				MSGS.getString("Text_Example") + " " + prefs.NAME_CATEGORY + "=\"Denver, Colorado\""));
		System.out.println(String.format(" \u21B3 %s (%s)",
				MSGS.getString("Text_Wiki_Page"),
				MSGS.getString("Text_Example") + " " + prefs.NAME_PAGE + "=\"Sandboarding\""));
		System.out.println(String.format(" \u21B3 %s (%s; %s)",
				MSGS.getString("Text_Local_File"),
				MSGS.getString("Text_Example") + " " + prefs.NAME_FILE + "=\"Documents" + File.separator + "files.txt\"",
				MSGS.getString("Hint_File_Syntax")));
	}
}
