package app;

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Dialog.ModalityType;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.prefs.Preferences;

import javax.security.auth.login.LoginException;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;

import wiki.Wiki;

enum State {
	PRE_INIT, PRE_DOWNLOAD, TERMINATED
}

public class ImkerGUI extends ImkerBase {

	private File inputFile = null;
	private String inputPage = null;
	private String inputCategory = null;
	private final JFrame FRAME = new JFrame(PROGRAM_NAME);
	private final JButton MAIN_BUTTON = new JButton("");
	private final JRadioButton FILE_BUTTON = new JRadioButton(
			MSGS.getString("Text_From_File"));
	private final JRadioButton PAGE_BUTTON = new JRadioButton(
			MSGS.getString("Text_Page"));
	private final JRadioButton CATEGORY_BUTTON = new JRadioButton(
			MSGS.getString("Text_Category"));
	private final JTextField STATUS_TEXT_FIELD = new JTextField(45);
	private State state = State.PRE_INIT;
	private boolean hasSource = false;
	private boolean hasTarget = false;

	private static final int GAP = 12;

	// Preferences {
	final String PREFS_NODE_PATH = "imker/gui";
	final Preferences prefs = Preferences.userRoot().node(PREFS_NODE_PATH);

	final String KEY_WIKI_DOMAIN = "KEY_WIKI_DOMAIN";
	// }

	/**
	 * Decide which action to execute after the main button was pressed
	 */
	protected void handleAction() {
		switch (state) {
		case PRE_INIT:
			initialize();
			solveWindowsBug();
			break;
		case PRE_DOWNLOAD:
			try {
				download();
				verifyCheckSum();
				state = State.TERMINATED;
			} catch (Throwable e) {
				terminate(e);
				return;
			}
			break;
		case TERMINATED:
			preInit(true);
			break;
		default:
			// should never happen
			System.exit(-1);
		}
	}

	/**
	 * Terminate on invalid input; Otherwise try to fetch the file list while
	 * blocking the UI with a pop up
	 */
	private void initialize() {
		if (!verifyInput()) {
			STATUS_TEXT_FIELD.setText(MSGS.getString("Error_Invalid_Input"));
			MAIN_BUTTON.setText(MSGS.getString("Button_Reset"));
			state = State.TERMINATED;
			return;
		}
		try {
			setWiki(prefs.get(KEY_WIKI_DOMAIN, ImkerBase.PREF_WIKI_DOMAIN_DEFAULT));

			STATUS_TEXT_FIELD.setText(MSGS.getString("Status_Wait_For_List"));
			JProgressBar progressBarIndet = new JProgressBar();
			progressBarIndet.setIndeterminate(true);
			progressBarIndet.setStringPainted(true);
			progressBarIndet.setString(" ");
			executeWorker(MSGS.getString("Status_Crawling"),
					MSGS.getString("Hint_Crawling"), progressBarIndet,
					MSGS.getString("Text_Exit"), new SwingWorker<Void, Void>() {

						@Override
						protected Void doInBackground()
								throws FileNotFoundException, IOException,
								LoginException {
							fetchFileNames();
							return null;
						}
					});
		} catch (Throwable e) {
			terminate(e);
			return;
		}

		if (getFileNames().length == 0) {
			JOptionPane
					.showMessageDialog(FRAME, MSGS.getString("Error_No_Files"),
							MSGS.getString("Text_Warning"),
							JOptionPane.WARNING_MESSAGE);
			STATUS_TEXT_FIELD.setText(MSGS.getString("Error_No_Files"));
			MAIN_BUTTON.setText(MSGS.getString("Button_Reset"));
			state = State.TERMINATED;
			return;
		}
		STATUS_TEXT_FIELD.setText(String.format(
				MSGS.getString("Prompt_Download"), getFileNames().length));
		MAIN_BUTTON.setText(String.format(MSGS.getString("Button_Download"),
				getFileNames().length));
		state = State.PRE_DOWNLOAD;
	}

	/**
	 * Gracefully set state to State.TERMINATED and display an error message
	 * with the exception
	 * 
	 * @param e
	 *            the exception or error that occurred
	 */
	private void terminate(Throwable e) {

		e.printStackTrace();

		String details = "Version: " + VERSION + "\n"
				+ "Stack trace:" + "\n"
				+ "```java" + "\n"
				+ "%s" + "\n"
				+ "```" + "\n";
		StringWriter sw = new StringWriter();
		e.printStackTrace(new PrintWriter(sw));
		details = String.format(details, sw.toString());
		String paramTitle = "[Imker] Exception";
		String paramDetails = null;
		try {
			paramTitle = URLEncoder.encode(paramTitle, "UTF-8");
			paramDetails = URLEncoder.encode(details, "UTF-8");
		} catch (UnsupportedEncodingException ignore) {
		}
		JTextArea ep = new JTextArea(e.toString() + "\n"
				+ MSGS.getString("Hint_Github_Issue") + "\n"
				+ String.format(GITHUB_ISSUE_TRACKER, paramTitle, paramDetails) + "\n"
				+ "And include the following details:\n"
				+ details);
		ep.setEditable(false);
		ep.setFocusable(true);

		JScrollPane msgScrollable = new JScrollPane(ep);
		msgScrollable.setPreferredSize(new Dimension(500, 400));
		JOptionPane.showMessageDialog(FRAME, msgScrollable,
				MSGS.getString("Status_Exception_Caught"),
				JOptionPane.ERROR_MESSAGE);
		STATUS_TEXT_FIELD.setText(MSGS.getString("Status_Exception_Caught"));
		MAIN_BUTTON.setText(MSGS.getString("Button_Reset"));
		state = State.TERMINATED;
	}

	/**
	 * Execute the worker and disable interaction by a modal dialog during
	 * execution; Exit when an InterruptedException occurs, otherwise Exceptions
	 * are thrown
	 * 
	 * @param dialogTitle
	 *            the text in the title bar of the modal dialog
	 * @param infoText
	 *            the text in the info section
	 * @param progressBar
	 *            the modal dialog's progress bar
	 * @param exitButton
	 *            the exit button label
	 * @param worker
	 *            the worker
	 * @throws Throwable
	 *             if some sort of issue occurs during task execution
	 */
	private void executeWorker(String dialogTitle, String infoText,
			JProgressBar progressBar, String exitButton,
			final SwingWorker<Void, Void> worker) throws Throwable {

		final JDialog modalDialog = new JDialog(FRAME, dialogTitle + " - "
				+ PROGRAM_NAME, ModalityType.APPLICATION_MODAL);
		modalDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
		modalDialog.setResizable(false);

		JPanel dialogContent = new JPanel(new BorderLayout(GAP, GAP));
		dialogContent.setBorder(BorderFactory.createEmptyBorder(GAP, GAP, GAP,
				GAP));
		dialogContent.add(new JLabel(infoText), BorderLayout.PAGE_START);
		dialogContent.add(progressBar, BorderLayout.CENTER);
		Button exit = new Button(exitButton);
		dialogContent.add(exit, BorderLayout.PAGE_END);

		modalDialog.add(dialogContent);
		modalDialog.pack();
		modalDialog.setLocationRelativeTo(FRAME);

		worker.addPropertyChangeListener(new PropertyChangeListener() {

			@Override
			public void propertyChange(PropertyChangeEvent evt) {
				if (evt.getPropertyName().equals("state")
						&& evt.getNewValue() == SwingWorker.StateValue.DONE) {
					modalDialog.dispose();
				}
			}
		});
		exit.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				System.exit(0);
				// // TODO do not exit?
				// worker.cancel(true);
				// try {
				// worker.get();
				// } catch (InterruptedException | ExecutionException ignore) {
				// }
				// modalDialog.dispose();
				// STATUS_TEXT_FIELD.setText(MSGS.getString("Status_Cancelled"));
				// MAIN_BUTTON.setText(MSGS.getString("Button_Reset"));
				// state.setState(State.TERMINATED);
				// cancelled = true;
				// // in executeWorker() calling function:
				// if (cancelled)
				// doSthDifferent();
			}
		});

		worker.execute();

		// block this thread until dialog is disposed
		modalDialog.setVisible(true);

		try {
			worker.get();
		} catch (ExecutionException e) {
			throw e.getCause();
		} catch (InterruptedException e) {
			// should not happen
			e.printStackTrace();
			System.exit(-1);
		}
	}

	/**
	 * Warn the user about invalid input
	 * 
	 * @return if the input is valid
	 */
	private boolean verifyInput() {
		if (CATEGORY_BUTTON.isSelected()) {
			if (inputCategory == null || inputCategory.length() == 0) {
				JOptionPane.showMessageDialog(FRAME,
						MSGS.getString("Error_Invalid_Name_Category") + " \""
								+ inputCategory + "\"",
						MSGS.getString("Title_Invalid_Name"),
						JOptionPane.WARNING_MESSAGE);
				return false;
			}
		} else if (PAGE_BUTTON.isSelected()) {
			if (inputPage == null || inputPage.length() == 0) {
				JOptionPane.showMessageDialog(FRAME,
						MSGS.getString("Error_Invalid_Name_Page") + " \""
								+ inputPage + "\"",
						MSGS.getString("Title_Invalid_Name"),
						JOptionPane.WARNING_MESSAGE);
				return false;
			}
		}
		return true;
	}

	private void solveWindowsBug() {
		if (!checkWindowsBug())
			return;

		String[] options = { MSGS.getString("Text_Replace_Chars"),
				MSGS.getString("Text_Exit") };
		int userSelection = JOptionPane
				.showOptionDialog(FRAME, MSGS.getString("Hint_Windows_Bug"),
						MSGS.getString("Title_Windows_Char_Bug") + " - "
								+ PROGRAM_NAME, JOptionPane.OK_CANCEL_OPTION,
						JOptionPane.WARNING_MESSAGE, null, options, options[0]);

		if (userSelection != 0)
			System.exit(0);
	}

	/**
	 * Try to download while blocking the UI with a modal dialog
	 * @throws Throwable
	 */
	private void download() throws Throwable {
		final JProgressBar progressBarDownload = new JProgressBar(0,
				getFileNames().length);
		progressBarDownload.setStringPainted(true);
		executeWorker(MSGS.getString("Status_Downloading"),
				MSGS.getString("Hint_Downloading"), progressBarDownload,
				MSGS.getString("Text_Exit"), new SwingWorker<Void, Void>() {

					@Override
					protected Void doInBackground()
							throws FileNotFoundException, IOException,
							LoginException {
						downloadLoop(new StatusHandler() {

							@Override
							public void handle(int i, String fileName) {
								progressBarDownload.setValue(i);
								STATUS_TEXT_FIELD.setText("(" + (i + 1) + "/"
										+ getFileNames().length + "): "
										+ fileName);
							}

							@Override
							public void handleConclusion(String status) {
								// TODO invisible (instant redraw)
								// STATUS_TEXT_FIELD.setText(STATUS_TEXT_FIELD
								// .getText() + status);
							}

						});
						return null;
					}
				});
		// TODO invisible (instantly overwritten by verifyCheckSum()
		// STATUS_TEXT_FIELD.setText(MSGS.getString("Status_Download_Complete"));
		// MAIN_BUTTON.setText(MSGS.getString("Button_Reset"));
	}

	/**
	 * Try to verify the checksums while blocking the UI with a modal dialog
	 * @throws Throwable
	 */
	private void verifyCheckSum() throws Throwable {
		final int errors[] = new int[1];
		final JProgressBar progressBarChecksum = new JProgressBar(0,
				getFileStatuses().length);
		progressBarChecksum.setStringPainted(true);
		executeWorker(MSGS.getString("Status_Checksum"),
				MSGS.getString("Hint_Checksum"), progressBarChecksum,
				MSGS.getString("Text_Exit"), new SwingWorker<Void, Void>() {

					@Override
					protected Void doInBackground()
							throws FileNotFoundException,
							NoSuchAlgorithmException, IOException {
						errors[0] = checksumLoop(new StatusHandler() {

							@Override
							public void handle(int i, String fileName) {
								progressBarChecksum.setValue(i);
								STATUS_TEXT_FIELD.setText("(" + (i + 1) + "/"
										+ getFileStatuses().length + "): "
										+ fileName);
							}

							@Override
							public void handleConclusion(String conclusion) {
								// TODO invisible (instant redraw)
								// STATUS_TEXT_FIELD.setText(STATUS_TEXT_FIELD
								// .getText() + conclusion);
							}

						});
						return null;
					}
				});
		STATUS_TEXT_FIELD.setText(String.format(
				MSGS.getString("Status_Checksum_Complete"), errors[0]));
		MAIN_BUTTON.setText(MSGS.getString("Button_Reset"));

		if (errors[0] == 0)
			return;

		String errorSample = "";
		int maxLines = 10;
		int currLines = 0;
		for (int i = 0; i < getFileStatuses().length; i++) {
			if (getFileStatuses()[i] == FileStatus.CHECKSUM_ERROR)
				if (currLines < maxLines) {
					errorSample += "\n * " + getFileNames()[i];
					currLines++;
				} else {
					errorSample += "\n ...";
					break;
				}
		}

		String[] options = new String[] { MSGS.getString("Button_Delete"),
				MSGS.getString("Button_Option_No") };
		int userSelection = JOptionPane.showOptionDialog(FRAME,
				String.format(MSGS.getString("Hint_Files_Corrupt"), errors[0])
						+ errorSample,
				MSGS.getString("Status_Checksum_Warning") + " - "
						+ PROGRAM_NAME, JOptionPane.YES_NO_OPTION,
				JOptionPane.QUESTION_MESSAGE, null, options, options[1]);

		if (userSelection == 1)
			return; // Selected "No"

		for (int i = 0; i < getFileStatuses().length; i++) {
			if (getFileStatuses()[i] == FileStatus.CHECKSUM_ERROR)
				Files.delete(new File(getOutputFolder().getPath()
						+ File.separator
						+ getFileNames()[i].substring(getFilePrefixLenght()))
						.toPath());
		}

		STATUS_TEXT_FIELD.setText(MSGS.getString("Status_Checksum_Deleted"));
		JOptionPane.showMessageDialog(FRAME,
				MSGS.getString("Status_Reset_Needed"),
				MSGS.getString("Title_Info") + " - " + PROGRAM_NAME,
				JOptionPane.INFORMATION_MESSAGE);
	}

	/**
	 * Set the state to State.PRE_INIT if the current state is not
	 * State.TERMINATED
	 * 
	 * @param force
	 *            ignore the current state
	 */
	private void preInit(boolean force) {
		if (!force && state == State.TERMINATED)
			return;
		STATUS_TEXT_FIELD.setText(MSGS.getString("Status_Select_InOut"));
		MAIN_BUTTON.setText(MSGS.getString("Button_GetList"));
		resetMemory();
		state = State.PRE_INIT;
	}

	/**
	 * Fetch the file list
	 * 
	 * @throws FileNotFoundException
	 *             if the local file is not found
	 * @throws IOException
	 *             if an IO issue occurs
	 * @throws LoginException
	 */
	protected void fetchFileNames() throws FileNotFoundException, IOException,
			LoginException {
		final String[] fileNames;
		if (CATEGORY_BUTTON.isSelected()) {
			STATUS_TEXT_FIELD.setText(MSGS.getString("Status_Get_Category")
					+ " ...");
			fileNames = (String[]) attemptFetch(new WikiAPI() {

				@Override
				public String[] fetch() throws IOException {
					// TODO: add argument to scan subcats
					boolean subcat = false;
					return getWiki().getCategoryMembers(inputCategory, subcat,
							Wiki.FILE_NAMESPACE);
				}
			}, MAX_FAILS, MAX_EXCEPTION_SLEEP_TIME);
		} else if (PAGE_BUTTON.isSelected()) {
			STATUS_TEXT_FIELD.setText(MSGS.getString("Status_Get_Page")
					+ " ...");
			fileNames = getImagesOnPage(inputPage, true);
		} else { // fileButton.isSelected()
			STATUS_TEXT_FIELD.setText(MSGS.getString("Status_Parse_File")
					+ " ...");
			fileNames = parseFileNames(inputFile.getPath());
		}
		setFileNames(fileNames);
		setFileStatuses(new FileStatus[fileNames.length]);
	}

	/**
	 * Read the content from the textField
	 * 
	 * @param textField
	 *            the textField to read from
	 * @param updateCategory
	 *            if the inputCategory should be updated or the inputPage;
	 *            Warning: The textField MUST be the corresponding
	 *            categoryTextField or pageTextField
	 */
	protected void parseText(JTextField textField, boolean updateCategory) {
		String newValue = textField.getText();
		if (updateCategory) {
			newValue = newValue.replaceFirst("(?i)^\\s*(category\\s*:)?\\s*",
					"");
			inputCategory = newValue;
			textField.setText(newValue);
		} else { // updatePage
			newValue = newValue.replaceFirst("\\s*", "");
			inputPage = newValue;
			textField.setText(newValue);
		}

		hasSource = true;
		MAIN_BUTTON.setEnabled(hasSource && hasTarget);
		// Also reset because text was replaced!
		preInit(false);
	}

	/**
	 * Add the output options to the GUI
	 * 
	 * @param boxPane
	 *            the GUI container
	 */
	private void addOutputBox(Container boxPane) {
		JPanel output = new JPanel(new BorderLayout(GAP, GAP));
		output.setBorder(new TitledBorder(MSGS.getString("Title_Output_Folder")));
		JPanel outputOptions = new JPanel(new BorderLayout(GAP, GAP));
		outputOptions.setBorder(new EmptyBorder(GAP, GAP, GAP, GAP));
		final JTextField currFolder = new JTextField();
		currFolder.setEditable(false);
		outputOptions.add(currFolder, BorderLayout.CENTER);
		JButton folderSelect = new JButton(MSGS.getString("Button_Choose"));
		folderSelect.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				JFileChooser folderChooser = new JFileChooser(getOutputFolder());
				folderChooser
						.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
				folderChooser.setAcceptAllFileFilterUsed(false);
				folderChooser.showOpenDialog(null);

				setOutputFolder(folderChooser.getSelectedFile());
				if (getOutputFolder() != null) {
					currFolder.setText(getOutputFolder().toString());
					hasTarget = true;
					MAIN_BUTTON.setEnabled(hasSource && hasTarget);
				}
			}
		});
		outputOptions.add(folderSelect, BorderLayout.LINE_END);

		output.add(outputOptions);
		boxPane.add(output);
	}

	/**
	 * Add the input option to the GUI
	 * 
	 * @param boxPane
	 *            the GUI container
	 */
	private void addInputBox(Container boxPane) {
		JPanel input = new JPanel(new BorderLayout(GAP, GAP));
		input.setBorder(new TitledBorder(MSGS.getString("Title_Source")));

		JPanel inputContainer = new JPanel(new BorderLayout(GAP, GAP));
		inputContainer.setBorder(new EmptyBorder(GAP, GAP, GAP, GAP));
		JPanel radioButtons = new JPanel(new GridLayout(3, 1, GAP / 2, GAP / 2));
		JPanel values = new JPanel(new GridLayout(3, 1, GAP / 2, GAP / 2));
		JPanel fileChoosePanel = new JPanel(new BorderLayout(GAP, GAP));

		CATEGORY_BUTTON.setSelected(true);
		CATEGORY_BUTTON.addItemListener(new ItemListener() {

			@Override
			public void itemStateChanged(ItemEvent arg0) {
				// Reset internal state
				if (CATEGORY_BUTTON.isSelected())
					preInit(false);
			}
		});
		radioButtons.add(CATEGORY_BUTTON);
		CATEGORY_BUTTON.setToolTipText(MSGS.getString("Hint_Src_Category"));
		final JTextField categoryTextField = new JTextField();
		categoryTextField.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent arg0) {
				parseText(categoryTextField, true);
			}
		});
		categoryTextField.addFocusListener(new FocusListener() {

			@Override
			public void focusLost(FocusEvent arg0) {
				parseText(categoryTextField, true);
			}

			@Override
			public void focusGained(FocusEvent e) {
				CATEGORY_BUTTON.setSelected(true);
			}

		});
		values.add(categoryTextField);
		categoryTextField.setToolTipText(MSGS.getString("Hint_Src_Category"));

		PAGE_BUTTON.addItemListener(new ItemListener() {

			@Override
			public void itemStateChanged(ItemEvent arg0) {
				// Reset
				if (PAGE_BUTTON.isSelected())
					preInit(false);
			}
		});
		radioButtons.add(PAGE_BUTTON);
		PAGE_BUTTON.setToolTipText(MSGS.getString("Hint_Src_Page"));
		final JTextField pageTextField = new JTextField();
		pageTextField.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				parseText(pageTextField, false);

			}
		});
		pageTextField.addFocusListener(new FocusListener() {

			@Override
			public void focusLost(FocusEvent e) {

				parseText(pageTextField, false);
			}

			@Override
			public void focusGained(FocusEvent e) {

				PAGE_BUTTON.setSelected(true);
			}
		});
		values.add(pageTextField);
		pageTextField.setToolTipText(MSGS.getString("Hint_Src_Page"));

		FILE_BUTTON.addItemListener(new ItemListener() {

			@Override
			public void itemStateChanged(ItemEvent arg0) {
				// Reset
				if (FILE_BUTTON.isSelected())
					preInit(false);
			}
		});
		radioButtons.add(FILE_BUTTON);
		FILE_BUTTON.setToolTipText(MSGS.getString("Hint_File_Syntax"));
		FILE_BUTTON.setEnabled(false);
		final JTextField currFile = new JTextField();
		currFile.setEditable(false);
		currFile.setToolTipText(MSGS.getString("Hint_File_Syntax"));
		fileChoosePanel.add(currFile, BorderLayout.CENTER);

		JButton infileSelect = new JButton(MSGS.getString("Button_Choose"));
		infileSelect.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				JFileChooser fileChooser = new JFileChooser();
				fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
				fileChooser.showOpenDialog(null);

				inputFile = fileChooser.getSelectedFile();
				if (inputFile == null) {
					hasSource = false;
					currFile.setText(null);
					FILE_BUTTON.setEnabled(false);
					// Select one of the other buttons instead
					if (FILE_BUTTON.isSelected())
						CATEGORY_BUTTON.setSelected(true);
				} else {
					hasSource = true;
					currFile.setText(inputFile.toString());
					FILE_BUTTON.setSelected(true);
					FILE_BUTTON.setEnabled(true);
					MAIN_BUTTON.setEnabled(hasSource && hasTarget);
				}
			}
		});
		infileSelect.setToolTipText(MSGS.getString("Hint_File_Syntax"));
		fileChoosePanel.add(infileSelect, BorderLayout.LINE_END);

		values.add(fileChoosePanel);

		ButtonGroup inputGroup = new ButtonGroup();
		inputGroup.add(CATEGORY_BUTTON);
		inputGroup.add(PAGE_BUTTON);
		inputGroup.add(FILE_BUTTON);

		inputContainer.add(radioButtons, BorderLayout.LINE_START);
		inputContainer.add(values, BorderLayout.CENTER);

		input.add(inputContainer, BorderLayout.CENTER);
		boxPane.add(input);
	}

	/**
	 * Add status panel to the GUI
	 * 
	 * @param boxPane
	 *            the GUI container
	 */
	private void addStatusPanel(Container boxPane) {
		JPanel statusPanel = new JPanel(new BorderLayout(GAP, GAP));
		statusPanel.setBorder(new TitledBorder(MSGS.getString("Title_Status")));

		STATUS_TEXT_FIELD.setEditable(false);
		STATUS_TEXT_FIELD.setBorder(new EmptyBorder(GAP, GAP, GAP, GAP));
		STATUS_TEXT_FIELD.setHorizontalAlignment(JTextField.CENTER);
		statusPanel.add(STATUS_TEXT_FIELD);
		boxPane.add(statusPanel);
	}

	/**
	 * Add the main button to the GUI
	 * 
	 * @param boxPane
	 *            the GUI container
	 */
	private void addActionButton(Container boxPane) {
		Font oldFont = MAIN_BUTTON.getFont();
		MAIN_BUTTON.setFont(oldFont.deriveFont(oldFont.getSize2D()
				/ (float) .75));
		MAIN_BUTTON.setAlignmentX(Component.CENTER_ALIGNMENT);
		MAIN_BUTTON.setEnabled(false);
		MAIN_BUTTON.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent arg0) {
				handleAction();
			}
		});
		boxPane.add(MAIN_BUTTON);
	}

	/**
	 * Add the header with the logo to the GUI
	 * 
	 * @param boxPane
	 *            the GUI container
	 */
	private void addHeader(Container boxPane) {
		Container header = new Container();
		header.setLayout(new BoxLayout(header, BoxLayout.X_AXIS));

		header.add(new JLabel(new ImageIcon(ImkerGUI.class.getResource("/pics/logo-60.png"))));

		header.add(Box.createHorizontalStrut(4 * GAP));

		JLabel versionLabel = new JLabel(VERSION);
		versionLabel.setFont(versionLabel.getFont().deriveFont(Font.PLAIN));
		versionLabel.setAlignmentY(0);
		header.add(versionLabel);

		header.add(Box.createHorizontalStrut(4 * GAP));

		JButton preferencesLabel = new JButton("\u2699" // \u2699 is unicode for ⚙
				+ " " + MSGS.getString("Text_Preferences"));
		preferencesLabel.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				popupPreferences();
			}
		});
		header.add(preferencesLabel);

		boxPane.add(header);

		Container description = new Container();
		description.setLayout(new FlowLayout(FlowLayout.CENTER, GAP, GAP));
		description.add(new JLabel(MSGS.getString("Description_Program")));
		boxPane.add(description);
	}

	private void popupPreferences() {
		JPanel wikiDomain = new JPanel(new FlowLayout());
		final JTextField wikiDomainField;
		{
			wikiDomainField = new JTextField(prefs.get(KEY_WIKI_DOMAIN, ImkerBase.PREF_WIKI_DOMAIN_DEFAULT));
			JTextArea wikiDomainText = new JTextArea(MSGS.getString("Description_Wiki_Domain") + ":");
			wikiDomainText.setEditable(false);
			wikiDomainText.setFocusable(false);
			wikiDomainText.setBackground(new Color(0, 0, 0, 0));
			wikiDomain.add(wikiDomainText);
			wikiDomain.add(wikiDomainField);
		}

		final JButton resetButton = new JButton(MSGS.getString("Button_Restore_Defaults"));
		{
			resetButton.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					prefs.put(KEY_WIKI_DOMAIN, ImkerBase.PREF_WIKI_DOMAIN_DEFAULT);
					wikiDomainField.setText(ImkerBase.PREF_WIKI_DOMAIN_DEFAULT);
				}
			});
		}
		JPanel prefsPanel = new JPanel();
		prefsPanel.setLayout(new BoxLayout(prefsPanel, BoxLayout.Y_AXIS));
		prefsPanel.add(Box.createVerticalStrut(GAP));
		prefsPanel.add(wikiDomain);
		prefsPanel.add(Box.createVerticalStrut(GAP));
		prefsPanel.add(resetButton);
		prefsPanel.add(Box.createVerticalStrut(GAP));

		final JDialog modalDialog = new JDialog(FRAME, MSGS.getString("Text_Preferences") + " - " + PROGRAM_NAME,
				ModalityType.APPLICATION_MODAL);
		modalDialog.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosed(WindowEvent e) {
				prefs.put(KEY_WIKI_DOMAIN, wikiDomainField.getText());
				preInit(false);
			}

			@Override
			public void windowClosing(WindowEvent e) {
				modalDialog.dispose();
			}
		});
		modalDialog.add(prefsPanel);
		modalDialog.pack();
		modalDialog.setMinimumSize(modalDialog.getSize());
		modalDialog.setLocationRelativeTo(FRAME);
		modalDialog.setVisible(true);
	}

	/**
	 * Create and show the GUI
	 */
	private void createAndShowGUI() {
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (Exception ignore) {
			// who cares?
		}
		FRAME.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		List<Image> icons = new ArrayList<Image>();
		for (int px = 512; px >= 16; px = px / 2) {
			icons.add(Toolkit.getDefaultToolkit().getImage(
					ImkerGUI.class.getResource("/pics/icon-" + px + ".png")));
		}
		FRAME.setIconImages(icons);

		// Set up the content pane.
		Container pane = FRAME.getContentPane();

		pane.setLayout(new BoxLayout(pane, BoxLayout.X_AXIS));
		pane.add(Box.createHorizontalStrut(GAP)); // left space

		Container boxPane = new JPanel();
		boxPane.setLayout(new BoxLayout(boxPane, BoxLayout.Y_AXIS));

		// LOGO + version + description + preferences
		addHeader(boxPane);

		// input -- output
		addInputBox(boxPane);
		boxPane.add(Box.createVerticalStrut(GAP));
		addOutputBox(boxPane);
		boxPane.add(Box.createVerticalStrut(GAP));

		// Action button
		addActionButton(boxPane);

		// Status
		addStatusPanel(boxPane);

		boxPane.add(Box.createVerticalStrut(3 * GAP));

		pane.add(boxPane);
		pane.add(Box.createHorizontalStrut(GAP)); // right space

		FRAME.pack();
		FRAME.setVisible(true);
		FRAME.setMinimumSize(FRAME.getSize());
	}

	public static void main(String[] args) {

		final ImkerGUI gui = new ImkerGUI();

		javax.swing.SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				gui.createAndShowGUI();
				gui.preInit(true);
			}
		});

	}
}
