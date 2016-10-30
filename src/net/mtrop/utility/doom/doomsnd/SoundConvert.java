/*******************************************************************************
 * Copyright (c) 2013-2014 Matt Tropiano
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser Public License v2.1
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/lgpl-2.1.html
 ******************************************************************************/
package net.mtrop.utility.doom.doomsnd;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import com.blackrook.commons.Common;
import com.blackrook.commons.list.List;
import com.blackrook.io.files.SoundFileInfo;
import com.blackrook.io.files.wav.WAVFile;
import com.blackrook.utility.Context;
import com.blackrook.utility.Settings;
import com.blackrook.utility.Utility;
import com.blackrook.utility.Version;

import net.mtrop.doom.sound.DMXSound;

/**
 * Converts sound to Doom Format sounds.
 * @author Matthew Tropiano
 */
public class SoundConvert extends Utility<SoundConvert.SoundContext>
{
	private static final Version VERSION = new Version(0,9,0,0);

	/** File path. */
	public static final String SETTING_FILES = "files";

	/**
	 * Context.
	 */
	public static class SoundContext implements Context
	{
	}

	@Override
	public Version getVersion()
	{
		return VERSION;
	}

	@Override
	public Settings getSettingsFromCMDLINE(String... args)
	{
		List<String> files = new List<String>();
		Settings out = new Settings();

		for (String a : args)
		{
			files.add(a);
		}
				
		String[] filePaths = new String[files.size()];
		files.toArray(filePaths);
		out.put(SETTING_FILES, filePaths);
		
		return out;
	}

	@Override
	public SoundContext createNewContext()
	{
		return new SoundContext();
	}

	// Opens a wave file.
	private WAVFile openWAVFile(String path)
	{
		WAVFile wav = null;
		try {
			wav = new WAVFile(path);
		} catch (SecurityException ex) {
			out.printf("ERROR: Couldn't open %s. Access denied.\n", path);
		} catch (IOException ex) {
			out.printf("ERROR: Couldn't open %s. Read error encountered, or not a WAV.\n", path);
		}
		return wav;
	}
	
	// Reads the samples in a WAV file (and does dumb mixing).
	private double[] readSamples(String path, WAVFile wav)
	{
		SoundFileInfo info = wav.getSoundInfo();
		double[][] inSamples = new double[info.getChannels()][((int)wav.getDataLength()) / info.getBytesPerSample() / info.getChannels()]; 
		
		int sampleCount = 0;
		
		try {
			sampleCount = wav.readSamples(inSamples);
		} catch (IOException e) {
			out.printf("ERROR: Couldn't read sound information from \"%s\".\n", path);
			return null;
		}

		if (info.getChannels() == 1)
		{
			return inSamples[0];
		}
		else
		{
			// mix channels (lazily) if more than one.
			double mixFactor = 1.0 / info.getChannels();
			double[] out = new double[sampleCount];
			for (int i = 0; i < out.length; i++)
			{
				for (double[] d : inSamples)
					out[i] += mixFactor * d[i]; 
			}
			return out;
		}
	}
	
	// Gets the new filename to use for an output file.
	private String getNewFileName(String path)
	{
		String extension = Common.getFileExtension(path);
		if (extension.length() > 0)
		{
			int index = path.indexOf("."+extension);
			return path.substring(0, index) + ".snd";
		}
		else
			return path + ".snd";
	}
	
	// Writes to a new file.
	private boolean writeOutputFile(DMXSound data, String path)
	{
		File f = new File(path);
		FileOutputStream fos = null;
		try {
			fos = new FileOutputStream(f);
			data.writeBytes(fos);
		} catch (SecurityException e) {
			out.printf("ERROR: Couldn't write sound file \"%s\". Access denied.\n", path);
			return false;
		} catch (IOException e){
			out.printf("ERROR: Couldn't write sound file \"%s\".\n", path);
			return false;
		} finally {
			Common.close(fos);
		}
		
		out.printf("Wrote \"%s\" successfully.\n", path);
		return true;
	}
	
	// Prints the usage message.
	private void printUsage()
	{
		out.println("Usage: doomsnd [wavefiles]");
		out.println("    [wavefiles]: A valid WAV file or files. Accepts wildcards");
		out.println("                 for multiple files.");
	}
	
	@Override
	public int execute(SoundContext context, Settings settings)
	{
		out.printf("DoomSND v%s by Matt Tropiano\n", getVersion());
		String[] filePaths = (String[])settings.get(SETTING_FILES);
		
		if (filePaths == null || filePaths.length == 0)
		{
			out.println("ERROR: No WAVs specified.");
			printUsage();
			return 2;
		}

		boolean successfulOnce = false;
		
		WAVFile wav = null;
		for (String f : filePaths)
		{
			wav = openWAVFile(f);
			if (wav != null)
			{
				double[] samples = readSamples(f, wav);
				if (samples != null)
				{
					DMXSound sd = new DMXSound(wav.getSoundInfo().getSampleRate(), samples);
					if (writeOutputFile(sd, getNewFileName(f)))
						successfulOnce = true;
				}
			}
			
			Common.close(wav);
		}
		
		if (!successfulOnce)
			return 1;

		return 0;
	}
	
}
