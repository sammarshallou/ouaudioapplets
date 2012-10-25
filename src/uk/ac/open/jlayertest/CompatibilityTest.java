package uk.ac.open.jlayertest;

import java.io.*;

import uk.ac.open.audio.AudioException;
import uk.ac.open.audio.mp3.MP3Decoder;

/**
 * This command-line utility recursively searches a directory and analyses all
 * .mp3 files, reporting any files which do not work in JLayer.
 */
public class CompatibilityTest
{
	/**
	 * @param args Parameters
	 */
	public static void main(String[] args)
	{
		if(args.length != 1)
		{
			System.err.println(
				"To use this tester, run with a folder to check. All MP3 files in\n" +
				"that folder and all subfolders will be analysed.\n" +
				"\n" +
				"You can also check a single file if required.\n" +
				"\n" +
				"If you want to store the report in a file, redirect it using >.\n" +
				"\n" +
				"Examples:\n" +
				"java -jar jlayercompat.jar \\\\share\\folder\\subfolder\n" +
				"java -jar jlayercompat.jar myfile.mp3\n" +
				"java -jar jlayercompat.jar c:\\testfolder > report.txt\n" +
				"\n" +
				"Note: For use in scripts, this returns error level 0 if there are\n" +
				"no errors in the MP3(s), or 1 if there are errors.\n");
			System.exit(2);
		}

		File folder = new File(args[0]);
		if(!folder.exists())
		{
			System.err.println(
				"The specified path '" + folder + "' does not exist.\n\n" +
				"(Run this jar without a folder parameter for help.)\n");
			System.exit(2);
		}
		if(!folder.canRead())
		{
			System.err.println(
				"The specified path '" + folder + "' cannot be accessed.\n\n" +
				"(Run this jar without a folder parameter for help.)\n");
			System.exit(2);
		}

		try
		{
			if(folder.isDirectory())
			{
				boolean ok = checkFolder(folder);
				System.exit(ok ? 0 : 1);
			}
			else if(folder.isFile() && folder.getName().toLowerCase().endsWith(".mp3"))
			{
				boolean ok = checkFile(folder);
				System.exit(ok ? 0 : 1);
			}
			else
			{
				System.err.println(
					"The specified path '" + folder + "' is not a folder or MP3 file.\n\n" +
					"(Run this jar without a folder parameter for help.)\n");
				System.exit(2);
			}
		}
		catch(IOException e)
		{
			System.err.println("An error has occurred and files could not be checked.\n");
			e.printStackTrace();
		}
	}

	private static boolean checkFolder(File folder) throws IOException
	{
		// List files
		File[] files = folder.listFiles();
		if(files == null)
		{
			return true;
		}

		// Loop through files
		boolean ok = true;
		for(File file  : files)
		{
			if(file.isDirectory())
			{
				ok = checkFolder(file) && ok;
			}
			else if(file.getName().toLowerCase().endsWith(".mp3"))
			{
				ok = checkFile(file) && ok;
			}
		}
		return ok;
	}

	private static boolean checkFile(File mp3) throws IOException
	{
		try
		{
			// Print file path
			System.out.print(mp3 + ": ");

			// Now test it...
			MP3Decoder decoder = new MP3Decoder();
			decoder.init(new FileInputStream(mp3));
			while(true)
			{
				byte[] data = decoder.decode();
				if(data == null)
				{
					break;
				}
			}
			System.out.print("OK");
			return true;
		}
		catch(AudioException e)
		{
			System.out.print("FAILED");
			return false;
		}
		finally
		{
			// Always finish the line!
			System.out.println();
		}
	}
}
