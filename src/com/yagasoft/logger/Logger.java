/*
 * Copyright (C) 2011-2014 by Ahmed Osama el-Sawalhy
 *
 *		The Modified MIT Licence (GPL v3 compatible)
 * 			Licence terms are in a separate file (LICENCE.md)
 *
 *		Project/File: Logger/com.yagasoft.logger/Logger.java
 *
 *			Modified: 26-Jul-2014 (04:57:46)
 *			   Using: Eclipse J-EE / JDK 8 / Windows 8.1 x64
 */

package com.yagasoft.logger;


import java.awt.Color;
import java.awt.Font;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JFileChooser;
import javax.swing.JTextPane;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.html.HTMLEditorKit;

import com.yagasoft.logger.menu.panels.option.Options;


/**
 * This class contains static methods to log entries, display them in a stylised form, and save them to disk in a log file.<br />
 * <br />
 * Everything is initialised automatically when calling the first post or error or exception. (everything is static)<br/>
 * To show the window call {@link #showLogger()}, which does initialise the log as above if it was the first thing called.<br />
 * <br/>
 * <strong>Defaults:</strong> read 'README.md'.
 */
public class Logger
{

	// please, don't change the order of the fields.

	/** Constant: VERSION. */
	public static final String		VERSION			= "5.01.065";

	/** set when the log is accessible and ready. */
	public static boolean			initialised		= false;

	/* holds last posted date. This is used for postDateIfChanged method */
	private static String			date			= "";

	/* Max entries to show in logger. */
	private static int				maxEntries		= 500;

	// everything that has been logged so far in plain format.
	private static String			history			= "";

	// everything that has been logged in styled format.
	private static List<String>		historyStylised	= new ArrayList<String>();

	// last directory browsed to save a file
	private static Path				lastDirectory	= Paths.get(System.getProperty("user.home"));

	/* where the logs will be stored relative to project path. */
	private static final Path		OPTIONS_FILE	= Paths.get(System.getProperty("user.dir") + "/var/options.dat");

	/** Constant: SLOTS. */
	public static final Semaphore	SLOTS			= new Semaphore(1);

	private static enum EntryType
	{
		INFO,
		ERROR,
		EXCEPTION
	}

	////////////////////////////////////////////////////////////////////////////////////////
	// #region Style.
	//======================================================================================

	/* Font size for log window. */
	private static int			fontSize					= 11;

	private static final String	font						= "Verdana";

	//--------------------------------------------------------------------------------------
	// #region Colours.

	// a bit lighter than the one from the Color class.
	private static final Color	BLUE						= new Color(35, 40, 210);

	private static final Color	ORANGE						= new Color(170, 90, 0);
	private static final Color	LIGHT_BLUE					= new Color(0, 150, 150);

	// a bit darker than the one from the Color class.
	private static final Color	GREEN						= new Color(0, 140, 0);

	private static final Color	VIOLET						= new Color(150, 0, 255);
	private static final Color	DARK_RED					= new Color(230, 0, 0);

	// a bit darker than the one from the Color class.
	private static final Color	MAGENTA						= new Color(220, 0, 160);

	// colours to cycle through when displaying info with words wrapped in '`'.
	private static Color[]		colours						= { BLUE, ORANGE, LIGHT_BLUE, GREEN, VIOLET, DARK_RED, MAGENTA };

	// #endregion Colours.
	//--------------------------------------------------------------------------------------

	/** Default number of colours. */
	public static int			defaultNumberOfColours		= colours.length;

	/** Default coloured strings separator for {@link #infoColoured(String...)}. */
	public static String		defaultColouringSeparator	= " ";

	/** Default black last string flag for {@link #infoColouredSeparator(String, String...)}. */
	public static boolean		defaultBlackLastString		= false;

	/* style passed to getStyle method. */
	private enum Style
	{
		PLAIN,
		BOLD,
		ITALIC,
		BOLDITALIC
	}

	//======================================================================================
	// #endregion Style.
	////////////////////////////////////////////////////////////////////////////////////////

	////////////////////////////////////////////////////////////////////////////////////////
	// #region Initialisation.
	//======================================================================================

	/**
	 * Convenience method for {@link #initAndShowLogger(String, int, boolean)}, using defaults (' ', max, false).
	 */
	public static synchronized void initAndShowLogger()
	{
		initAndShowLogger(defaultColouringSeparator, defaultNumberOfColours, defaultBlackLastString);
	}

	/**
	 * Convenience method for {@link #initLogger(String, int, boolean)}.
	 *
	 * @param defaultSeparator
	 *            Default separator.
	 * @param defaultNumberOfColours
	 *            Default number of colours.
	 * @param defaultBlackLastString
	 *            Default black last string?
	 */
	public static synchronized void initAndShowLogger(String defaultSeparator, int defaultNumberOfColours
			, boolean defaultBlackLastString)
	{
		initLogger(defaultSeparator, defaultNumberOfColours, defaultBlackLastString);
		GUI.showLogger();
	}

	/**
	 * Convenience method for {@link #initLogger(String, int, boolean)}, using defaults (' ', max, false).
	 */
	public static synchronized void initLogger()
	{
		initLogger(defaultColouringSeparator, defaultNumberOfColours, defaultBlackLastString);
	}

	/**
	 * Initialises the logger by loading the options, initialising the log file, and the GUI.
	 *
	 * @param defaultSeparator
	 *            Default separator to use for {@link #infoColoured(String...)}
	 * @param defaultNumberOfColours
	 *            Default number of colours to use for {@link #info(String, int...)}
	 * @param defaultBlackLastString
	 *            Default black last string to use for {@link #infoColouredSeparator(int, boolean, String, String...)}
	 */
	public static synchronized void initLogger(String defaultSeparator, int defaultNumberOfColours
			, boolean defaultBlackLastString)
	{
		// set defaults
		Logger.defaultColouringSeparator = defaultSeparator;
		Logger.defaultNumberOfColours = defaultNumberOfColours;
		Logger.defaultBlackLastString = defaultBlackLastString;

		// initialise logger if it wasn't.
		if ( !initialised)
		{
			loadOptions();

			File.initFile();
			GUI.initLogger();

			initialised = true;

			// post something and create a log file for this session.
			info("!!! `NEW LOG` !!!");
		}
	}

	//======================================================================================
	// #endregion Initialisation.
	////////////////////////////////////////////////////////////////////////////////////////

	/**
	 * Show logger window.
	 */
	public static synchronized void showLogger()
	{
		GUI.showLogger();
	}

	/**
	 * Hide logger window.
	 */
	public static synchronized void hideLogger()
	{
		GUI.hideLogger();
	}

	// //////////////////////////////////////////////////////////////////////////////////////
	// #region Public posting interface.
	// ======================================================================================

	//--------------------------------------------------------------------------------------
	// #region Info posting.

	/**
	 * Informing log entry. You can use '`' character as to wrap words to be coloured. Colouring will cycle between 7 colours.
	 *
	 * @param entry
	 *            Entry.
	 * @param coloursToUse
	 *            Number of colours to use, excluding black (current max is 7), optional.
	 *            Anything outside 0..max results in max.
	 */
	public static synchronized void info(String entry, int... coloursToUse)
	{
		// write the time stamp, then the entry next to it.

		// time-stamp
		postTimeStamp();

		// line label
		AttributeSet style = getStyle(0, Style.ITALIC, GREEN);
		GUI.append("Info:   ", style);

		// append the entries on new lines using number of colours passed.
		postEntry(entry, coloursToUse);
	}

	/**
	 * Informing log entries. This will be posted one after the other in the same time-stamp.
	 * You can use '`' character as to wrap words to be coloured. Colouring will cycle between colours.
	 *
	 * @param coloursToUse
	 *            Number of colours to use, excluding black (current max is 7). Pass '-1' for max colours.
	 * @param entries
	 *            List of entries to post.
	 */
	public static synchronized void info(int coloursToUse, String... entries)
	{
		// write the time stamp
		postTimeStamp();

		// entry label.
		AttributeSet style = getStyle(0, Style.ITALIC, new Color(0, 150, 0));
		GUI.append("Info ...\n", style);

		// append the entry using number of colours passed.
		for (String entry : entries)
		{
			postEntry(entry, coloursToUse);
		}
	}

	// this method is the common process of posting entries for the two methods above.
	private static synchronized void postEntry(String entry, int... coloursToUse)
	{
		// split the entry into sections based on the delimiter '`'
		String[] entries = entry.split("`");

		// calculate number of colours to use. If passed, then use, else if -1 or not passed, then use default.
		int numberOfColours = Arrays.stream(coloursToUse).findFirst().orElse(defaultNumberOfColours);
		numberOfColours = (numberOfColours == -1) ? defaultNumberOfColours : numberOfColours;

		AttributeSet style = getStyle(Style.PLAIN);

		// iterate over entry sections
		for (int i = 0; i < entries.length; i++)
		{
			// reset style
			style = getStyle(Style.PLAIN);

			// odd entries are the ones needing colour
			if (((i % 2) == 1) && (numberOfColours > 0))
			{
				// post escaped entry using a different colour.
				style = getStyle(Style.PLAIN, colours[(i / 2) % numberOfColours]);
			}

			// add to log
			GUI.append(entries[i], style);
		}

		// add a new line
		GUI.append("\n", style);
	}

	/**
	 * Post strings, coloured using the max number of colours, and separated by the default set separator.
	 * It doesn't colour the last string as black.
	 *
	 * @param strings
	 *            Strings.
	 */
	public static synchronized void infoColoured(String... strings)
	{
		infoColouredSeparator(defaultColouringSeparator, strings);
	}

	/**
	 * Post strings, coloured using the number of colours passed, and separated by the default set separator.
	 *
	 * @param coloursToUse
	 *            Colours to use, -1 for max.
	 * @param blackLastString
	 *            Black last string?
	 * @param strings
	 *            Strings.
	 */
	public static synchronized void infoColoured(int coloursToUse, boolean blackLastString, String... strings)
	{
		infoColouredSeparator(coloursToUse, blackLastString, defaultColouringSeparator, strings);
	}

	/**
	 * Post strings, coloured using the max number of colours, and separated by the passed separator.
	 * It doesn't colour the last string as black.
	 *
	 * @param separator
	 *            Separator.
	 * @param strings
	 *            Strings.
	 */
	public static synchronized void infoColouredSeparator(String separator, String... strings)
	{
		infoColouredSeparator(defaultNumberOfColours, defaultBlackLastString, separator, strings);
	}

	/**
	 * <p>
	 * Post strings, coloured using the number of colours passed, and separated by the passed separator.
	 * </p>
	 *
	 * @param coloursToUse
	 *            Colours to use, -1 for max.
	 * @param blackLastString
	 *            Black last string?
	 * @param separator
	 *            Separator.
	 * @param strings
	 *            Strings.
	 */
	public static synchronized void infoColouredSeparator(int coloursToUse, boolean blackLastString
			, String separator, String... strings)
	{
		if (strings.length == 0)
		{
			return;
		}

		// form the entry
		// add the first string using the colouring symbol
		String entry = "`" + strings[0] + "`" + ((strings.length > 2) ? separator : "");

		// add the rest if there are any, except last string
		for (int i = 1; (i < (strings.length - 1)) && (strings.length > 2); i++)
		{
			entry += "`" + strings[i] + "`" + separator;
		}

		// end with the last string, and if 'black' is specified, then don't wrap it in '`'.
		if (strings.length > 2)
		{
			entry += (blackLastString ? "" : "`") + strings[strings.length - 1] + (blackLastString ? "" : "`");
		}

		info(entry, coloursToUse);
	}

	/**
	 * Post this string using {@link #infoColouredSeparator(String, String...)} after splitting it using the separator passed.
	 *
	 * @param separator
	 *            Separator.
	 * @param string
	 *            String.
	 */
	public static synchronized void infoColouredSequence(String separator, String string)
	{
		infoColouredSeparator(separator, string.split(separator));
	}

	/**
	 * Post this string using {@link #infoColouredSeparator(int, boolean, String, String...)} after splitting it using the
	 * separator passed.
	 *
	 * @param coloursToUse
	 *            Colours to use, -1 for max.
	 * @param blackLastString
	 *            Black last string?
	 * @param separator
	 *            Separator.
	 * @param string
	 *            String.
	 */
	public static synchronized void infoColouredSequence(int coloursToUse, boolean blackLastString
			, String separator, String string)
	{
		infoColouredSeparator(coloursToUse, blackLastString, separator, string.split(separator));
	}

	// #endregion Info posting.
	//--------------------------------------------------------------------------------------

	/**
	 * Error log entry. You can use '`' character as to wrap words to be coloured black.
	 *
	 * @param entry
	 *            Entry.
	 */
	public static synchronized void error(String entry)
	{
		// write the time stamp
		postTimeStamp();

		// append line label
		AttributeSet style = getStyle(0, Style.BOLDITALIC, Color.RED);
		GUI.append("!! ERROR >>   ", style);

		// append the error
		postError(entry);

		// add an extra label.
		style = getStyle(Style.BOLDITALIC, Color.RED);
		GUI.append("   << ERROR !!\n", style);

	}

	/**
	 * Error log entry. This will be posted one after the other in the same time-stamp.
	 * You can use '`' character as to wrap words to be coloured black.
	 *
	 * @param entries
	 *            Entries.
	 */
	public static synchronized void errors(String... entries)
	{
		// write the time stamp
		postTimeStamp();

		// append line label
		AttributeSet style = getStyle(0, Style.BOLDITALIC, Color.RED);
		GUI.append("!! ERRORS !!\n", style);

		// append the errors on new lines
		for (String entry : entries)
		{
			postError(entry);
		}
	}

	// this method is the common process of posting entries for the two methods above.
	private static synchronized void postError(String entry)
	{
		// split the entry into sections based on the delimiter '`'
		String[] entries = entry.split("`");

		AttributeSet style = getStyle(Style.PLAIN, Color.RED);

		// odd entries are the ones needing colour
		for (int i = 0; i < entries.length; i++)
		{
			// reset style
			style = getStyle(Style.PLAIN, Color.RED);

			if ((i % 2) == 1)
			{
				// post escaped entry using a different colour.
				style = getStyle(Style.PLAIN);
			}

			// add to log
			GUI.append(entries[i], style);
		}

		// add a new line
		GUI.append("\n", style);
	}

	/**
	 * Exception log entry.
	 *
	 * @param exception
	 *            the Exception.
	 */
	public static synchronized void except(Throwable exception)
	{
		// write the time stamp, then the exception below it.

		postTimeStamp();

		AttributeSet style = getStyle(0, Style.BOLDITALIC, Color.RED);
		GUI.append("!! EXCEPTION !!\n", style);

		// define how to handle the character in the stack trace.
		PrintWriter exceptionWriter = new PrintWriter(new Writer()
		{

			// store lines in the stack trace to print later.
			List<String>	lines	= new ArrayList<String>();

			@Override
			public void write(char[] cbuf, int off, int len) throws IOException
			{
				lines.add(new String(cbuf, off, len));
			}

			@Override
			public void flush() throws IOException
			{
				AttributeSet styleTemp = getStyle(0, Style.PLAIN, Color.RED);

				for (String line : lines)
				{
					GUI.append(line, styleTemp);		// send to the logger.
				}
			}

			@Override
			public void close() throws IOException
			{}
		});

		// print the exception.
		exception.printStackTrace(exceptionWriter);
		exceptionWriter.flush();
		exceptionWriter.close();

		GUI.append("\r", style);		// send to the logger.
	}

	// ======================================================================================
	// #endregion Public posting interface.
	// //////////////////////////////////////////////////////////////////////////////////////

	// add to stylised history.
	static synchronized void addToHistory(String entry, AttributeSet style)
	{
		history += entry;
		historyStylised.add(getHTML(entry, style));
	}

	// reduce the max entries to be within the limit
	static synchronized void trimLog()
	{
		if (getEntriesNum() > maxEntries)
		{
			setMaxEntries(maxEntries);
		}
	}

	/**
	 * Clear log.
	 */
	public static synchronized void clearLog()
	{
		if ( !initialised)
		{
			return;
		}

//		getShortHistoryStylised().clear();
		int temp = maxEntries;
		setMaxEntries(0);
		setMaxEntries(temp);
	}

	/**
	 * Gets the entries so far.
	 *
	 * @return the entriesNum
	 */
	public static synchronized int getEntriesNum()
	{
		try
		{
			String text = GUI.getTextPane().getDocument().getText(0, GUI.getTextPane().getDocument().getLength());
			Pattern pattern = Pattern.compile("\\d{2}/(\\w){3}/\\d{2} \\d{2}:\\d{2}:\\d{2} (AM|PM):");
			Matcher matcher = pattern.matcher(text);

			int count = 0;

			while (matcher.find())
			{
				count++;
			}

			return count;
		}
		catch (BadLocationException e)
		{
			e.printStackTrace();
			return 0;
		}
	}

	// //////////////////////////////////////////////////////////////////////////////////////
	// #region Text methods.
	// ======================================================================================

	/* post time stamp to log. */
	private static synchronized void postTimeStamp()
	{
//		postDateIfChanged();

		// post date in light colour because it's repeated too much, so it becomes distracting.
		AttributeSet style = getStyle(0, Style.PLAIN, new Color(180, 180, 180));
		GUI.append(getDate() + " ", style);

		// post time in black.
		style = getStyle(Style.PLAIN);
		GUI.append(getTime() + ": ", style);
	}

	/* posts the date if it has changed from the saved one -- saves space in the log. */
	private static synchronized void postDateIfChanged()
	{
		if ( !getDate().equals(date))
		{
			// save current date if it has changed.
			date = getDate();

			// post the new date in a bright colour.
			AttributeSet style = getStyle(15, Style.BOLD, new Color(255, 150, 0));
			GUI.append(getDate() + " ", style);
		}
	}

	/* create a current date string. */
	private static String getDate()
	{
		return new SimpleDateFormat("dd/MMM/yy").format(new Date()) /* DateFormat.getDateInstance().format(new Date()) */;
	}

	/* Create a current time string. */
	private static String getTime()
	{
		return new SimpleDateFormat("hh:mm:ss aa").format(new Date());
	}

	// convenience method
	private static AttributeSet getStyle(Style style, Color... colour)
	{
		return getStyle(fontSize, style, colour);
	}

	/*
	 * Forms and returns the style as an {@link AttributeSet} to be used with {@link JTextPane}.
	 * Pass '0' for size to use the default one. Colour is optional, black is used by default.
	 *
	 * Credit: Philip Isenhour (http://javatechniques.com/blog/setting-jtextpane-font-and-color/)
	 */
	private static AttributeSet getStyle(int size, Style style, Color... colour)
	{
		// if nothing is displayed, then no style is needed.
		if (GUI.getTextPane() == null)
		{
			return null;
		}

		// Start with the current input attributes for the JTextPane. This
		// should ensure that we do not wipe out any existing attributes
		// (such as alignment or other paragraph attributes) currently
		// set on the text area.
		MutableAttributeSet attributes = GUI.getTextPane().getInputAttributes();

		Font font = new Font(Logger.font
				, ((style == Style.BOLD) ? Font.BOLD : 0)
						+ ((style == Style.ITALIC) ? Font.ITALIC : 0)
						+ ((style == Style.BOLDITALIC) ? Font.ITALIC + Font.BOLD : 0)
				, (size <= 0) ? getFontSize() : size);

		// Set the font family, size, and style, based on properties of
		// the Font object. Note that JTextPane supports a number of
		// character attributes beyond those supported by the Font class.
		// For example, underline, strike-through, super- and sub-script.
		StyleConstants.setFontFamily(attributes, font.getFamily());
		StyleConstants.setFontSize(attributes, font.getSize());
		StyleConstants.setItalic(attributes, (font.getStyle() & Font.ITALIC) != 0);
		StyleConstants.setBold(attributes, (font.getStyle() & Font.BOLD) != 0);

		// Set the font colour, or black by default.
		StyleConstants.setForeground(attributes, Arrays.stream(colour).findFirst().orElse(Color.BLACK));

		return attributes;
	}

	// convert text and style to HTML.
	private static String getHTML(String text, AttributeSet style)
	{
		// use to convert text to HTML
		JTextPane conversionPane = new JTextPane();
		conversionPane.setEditorKit(new HTMLEditorKit());

		try
		{
			// replace characters that aren't parsed by the HTML panel!!!
			// add the plain text to an HTML editor to convert the text to a stylised HTML.
			conversionPane.getDocument().insertString(conversionPane.getCaretPosition()
					, text.replace("\n", "`new_line`").replace("\t", "`tab`")
							.replace(" ", "`space`")
					, style);

			MutableAttributeSet attributes = conversionPane.getInputAttributes();
			attributes.removeAttributes(attributes);
			attributes.addAttribute(StyleConstants.FontSize, 9);

			conversionPane.getStyledDocument().setCharacterAttributes(0, conversionPane.getDocument().getLength()
					, attributes, false);
		}
		catch (BadLocationException e)
		{
			e.printStackTrace();
			return "";
		}

		// remove unnecessary tags, and return tags that were replaced above.
		// get the text back from the editor as HTML.
		return conversionPane.getText().replace("<html>", "").replace("</html>", "").replace("<head>", "")
				.replace("</head>", "").replace("<body>", "").replace("</body>", "")
				.replace("`new_line`", "<br />").replace("`tab`", "&#9;").replace("`space`", "&nbsp;")
				.replace("<p style", "<span style").replace("</p>", "</span>");
	}

	// ======================================================================================
	// #endregion Text methods.
	// //////////////////////////////////////////////////////////////////////////////////////

	////////////////////////////////////////////////////////////////////////////////////////
	// #region Saving.
	//======================================================================================

	/**
	 * Save as html.
	 */
	public static void saveAsHTML()
	{
		if ( !initialised)
		{
			return;
		}

		// !comments are in saveAsTxt!

		Path chosenFolder = chooseFolder();

		if (chosenFolder == null)
		{
			return;
		}

		Path file = chosenFolder.resolve("log_file_-_" + File.getFileStamp() + ".html");
		Writer writer = null;

		try
		{
			Files.createFile(file);
			writer = new OutputStreamWriter(Files.newOutputStream(file));
			writer.write("<html><body>" + historyStylised.stream().reduce((e1, e2) -> e1 + e2).orElse("") + "</html></body>");
		}
		catch (IOException e)
		{
			Logger.except(e);
			e.printStackTrace();
		}
		finally
		{
			if (writer != null)
			{
				try
				{
					writer.close();
				}
				catch (IOException e)
				{
					e.printStackTrace();
					Logger.except(e);
				}
			}
		}
	}

	/**
	 * Save as txt.
	 */
	public static void saveAsTxt()
	{
		if ( !initialised)
		{
			return;
		}

		// ask the user for folder to save to.
		Path chosenFolder = chooseFolder();

		// if nothing is chosen, do nothing.
		if (chosenFolder == null)
		{
			return;
		}

		// get the full path of the file, using the parent, and a time stamp.
		Path file = chosenFolder.resolve("log_file_-_" + File.getFileStamp() + ".txt");
		Writer writer = null;

		try
		{
			// create an empty file, get a stream to it, and write the history.
			Files.createFile(file);
			writer = new OutputStreamWriter(Files.newOutputStream(file));
			writer.write(history.replace("\r", ""));
		}
		catch (IOException e)
		{
			Logger.except(e);
			e.printStackTrace();
		}
		finally
		{
			if (writer != null)
			{
				try
				{
					writer.close();
				}
				catch (IOException e)
				{
					e.printStackTrace();
					Logger.except(e);
				}
			}
		}
	}

	/**
	 * Choose folder.
	 *
	 * @return Local folder
	 */
	private static Path chooseFolder()
	{
		if (Files.notExists(lastDirectory))
		{
			lastDirectory = Paths.get(System.getProperty("user.home"));
		}

		// only choose directories.
		JFileChooser chooser = new JFileChooser(lastDirectory.toString());
		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

		// show dialogue
		int result = chooser.showOpenDialog(GUI.getFrame());
		java.io.File selectedFolder = chooser.getSelectedFile();

		// if a folder was not chosen ...
		if ((result != JFileChooser.APPROVE_OPTION) || (selectedFolder == null))
		{
			return null;
		}

		lastDirectory = Paths.get(selectedFolder.toURI());

		return lastDirectory;
	}

	/**
	 * Load options.
	 */
	public static synchronized void loadOptions()
	{
		if (Files.notExists(OPTIONS_FILE))
		{
			return;
		}

		FileInputStream fileStream;		// incoming link to file.
		ObjectInputStream objectStream;		// link to objects read from file.
		Options options;

		try
		{
			fileStream = new FileInputStream(OPTIONS_FILE.toString());
			objectStream = new ObjectInputStream(fileStream);
			options = (Options) objectStream.readObject();
			objectStream.close();

			maxEntries = options.numberOfEntries;
			fontSize = options.fontSize;
			GUI.setWrapVarOnly(options.wrap);
			GUI.setActionOnCloseVarOnly(options.actionOnClose);
			lastDirectory = Paths.get(options.lastDirectory);
		}
		catch (ClassNotFoundException | IOException e)
		{
			e.printStackTrace();
		}
	}

	/**
	 * Save options.
	 */
	public static synchronized void saveOptions()
	{
		FileOutputStream fileStream;
		ObjectOutputStream objectStream;
		Options options = new Options(maxEntries, fontSize, GUI.isWrap());
		options.actionOnClose = GUI.getActionOnClose();
		options.lastDirectory = lastDirectory.toString();

		try
		{
			fileStream = new FileOutputStream(OPTIONS_FILE.toString());
			objectStream = new ObjectOutputStream(fileStream);
			objectStream.writeObject(options);
			objectStream.close();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}

	//======================================================================================
	// #endregion Saving.
	////////////////////////////////////////////////////////////////////////////////////////

	////////////////////////////////////////////////////////////////////////////////////////
	// #region Getters and setters.
	//======================================================================================

	/**
	 * Gets the current log file.
	 *
	 * @return the current log file
	 */
	public static Path getCurrentLogFile()
	{
		return File.logFile;
	}

	/**
	 * Gets the max entries.
	 *
	 * @return the maxEntries
	 */
	public static int getMaxEntries()
	{
		return maxEntries;
	}

	/**
	 * Sets the max entries.
	 *
	 * @param maxEntries
	 *            the maxEntries to set
	 */
	public static synchronized void setMaxEntries(int maxEntries)
	{
		int entries = getEntriesNum();

		if ( !initialised || (maxEntries == entries))
		{
			return;
		}

		try
		{
			Logger.maxEntries = maxEntries;

			int count = 0;

			String text = GUI.getTextPane().getDocument().getText(0, GUI.getTextPane().getDocument().getLength());
			Pattern pattern = Pattern.compile("\\d{2}/(\\w){3}/\\d{2} \\d{2}:\\d{2}:\\d{2} (AM|PM):");
			Matcher matcher = pattern.matcher(text);

			// count the entries above the limit.
			while (matcher.find() && (entries > maxEntries))
			{
				count++;
				entries--;
			}

			// remove extra
			GUI.removeEntries(count);
		}
		catch (BadLocationException e)
		{
			e.printStackTrace();
		}
	}

	/**
	 * Gets the font size.
	 *
	 * @return the fontSize
	 */
	public static int getFontSize()
	{
		return fontSize;
	}

	/**
	 * Sets the font size.
	 *
	 * @param fontSize
	 *            the fontSize to set
	 */
	public static synchronized void setFontSize(int fontSize)
	{
		if ( !initialised || (Logger.fontSize == fontSize))
		{
			return;
		}

		Logger.fontSize = fontSize;

		try
		{
			Logger.SLOTS.acquire();

			MutableAttributeSet attributes = GUI.getTextPane().getInputAttributes();
			attributes.removeAttributes(attributes);
			attributes.addAttribute(StyleConstants.FontSize, fontSize);

			GUI.getTextPane().getStyledDocument().setCharacterAttributes(0, GUI.getTextPane().getDocument().getLength()
					, attributes, false);
		}
		catch (InterruptedException e)
		{
			e.printStackTrace();
		}
		finally
		{
			Logger.SLOTS.release();
		}

	}

	//======================================================================================
	// #endregion Getters and setters.
	////////////////////////////////////////////////////////////////////////////////////////

	////////////////////////////////////////////////////////////////////////////////////////
	// #region Extras.
	//======================================================================================

	// static class!
	private Logger()
	{}

	// tester method
	@SuppressWarnings("all")
	public static void main(String[] args)
	{
		initAndShowLogger(" <---~~---> ", 3, true);

//		info("TEST1!!!");
//		error("TEST2!!!");
//
//		hideLogger();
//
//		error("TEST3!!!");
//		info("TEST4!!!");
//
//		showLogger();

		info("teeeeeeeeest `teeeeeeeeeest` `teeeeeeeeest` `teseeeeeeeeeet` `teeeeeeeeeeest`"
				+ " `teeeeeeeeeeeeest` `teeeeeeeeest` `teeeeeeeeeest` `teeeeeeeeest` `teeeeeeeeeeeest` `teeeeeeeeeeeest`"
				+ " `teeeeeeeeeest` `teeeeeeeeeeeeeeeeeest`");
		info("teeeeeeeeest `teeeeeeeeeest` `teeeeeeeeest` `teseeeeeeeeeet` `teeeeeeeeeeest`"
				+ " `teeeeeeeeeeeeest` `teeeeeeeeest` `teeeeeeeeeest` `teeeeeeeeest` `teeeeeeeeeeeest` `teeeeeeeeeeeest`"
				+ " `teeeeeeeeeest` `teeeeeeeeeeeeeeeeeest`");
		info("teeeeeeeeest `teeeeeeeeeest` `teeeeeeeeest` `teseeeeeeeeeet` `teeeeeeeeeeest`"
				+ " `teeeeeeeeeeeeest` `teeeeeeeeest` `teeeeeeeeeest` `teeeeeeeeest` `teeeeeeeeeeeest` `teeeeeeeeeeeest`"
				+ " `teeeeeeeeeest` `teeeeeeeeeeeeeeeeeest`");

		error("teeeeeeeeeest `test` `test` `test` `test` test `test` `test` `test` `test` `test` test `test`");

		infoColoured("teeeeeeest", "teeeeeeest", "teeeeeeest", "teeeeeeest", "teeeeeeest", "teeeeeeest",
				"teeeeeeest"
				, "teeeeeeest", "teeeeeeest", "teeeeeeest", "teeeeeeest");

		infoColouredSequence(3, true, "------>", "asklajdlas------>skladjasldk------>asdjslad------>ksajfjjlfa------>asdadjkl");
//		clearLog();

		info( -1, new String[] { "tte `te` te `te` st", "te `st` tt", "final test `tt` ette `te` t" });

		errors(new String[] { "te `st` tt", "t `te` te `tete` st", "fi `nal te` st" });

		except(new Exception("TEST!"));

//		GUI.removeEntries(2);

//		shortHistoryStylised.stream().forEach(System.out::print);
//		System.out.println(shortHistoryStylised.stream().reduce(String::concat).orElse(""));

//		Pattern pattern = Pattern.compile("\\d{2}/(\\w){3}/\\d{2} \\d{2}:\\d{2}:\\d{2} (AM|PM):");
//		Matcher matcher;
//		try
//		{
//			matcher = pattern.matcher(GUI.getTextPane().getDocument().getText(0, GUI.getTextPane().getDocument().getLength()));
//			while(matcher.find())
//			{
//				System.out.println(matcher.group() + " -- " + matcher.start() + " -- " + matcher.end());
//			}
//		}
//		catch (BadLocationException e1)
//		{
//			e1.printStackTrace();
//		}

//		GUI.getTextPane().repaint();
//
//		info("`test`");
//		System.out.println(GUI.getTextPane().getText());

		new Thread(() -> {
			int count = 0;

			while (true)
			{
//				GUI.removeEntries(1);

				try
				{
					Thread.sleep(2000);
					info("`Test` >>> `" + count++ + "` <<< `!!!`");
				}
				catch (Exception e)
				{
					e.printStackTrace();
				}
			}
		}).start();

//		String temp = "<html><body>" + getHistoryStylised().stream().reduce(String::concat).orElse("") + "</body></html>";
//		System.out.println(temp);
//		GUI.getTextPane().setText(temp);
//		GUI.textPane.select(5, 20);
//		GUI.textPane.setEditable(true);
//		GUI.textPane.replaceSelection("");
//		GUI.textPane.setEditable(false);
//		GUI.textPane.setCaretPosition(GUI.textPane.getDocument().getLength());
		// must stop process manually.
	}

	//======================================================================================
	// #endregion Extras.
	////////////////////////////////////////////////////////////////////////////////////////

}
