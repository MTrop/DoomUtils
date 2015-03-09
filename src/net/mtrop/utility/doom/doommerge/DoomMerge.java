/*******************************************************************************
 * Copyright (c) 2013-2014 Matt Tropiano
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser Public License v2.1
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/lgpl-2.1.html
 ******************************************************************************/
package net.mtrop.utility.doom.doommerge;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;

import net.mtrop.doom.WadBuffer;
import net.mtrop.doom.WadEntry;
import net.mtrop.doom.WadFile;

import com.blackrook.commons.Common;
import com.blackrook.commons.list.List;
import com.blackrook.utility.Command;
import com.blackrook.utility.Context;
import com.blackrook.utility.Executor;
import com.blackrook.utility.Executor.ScriptException;
import com.blackrook.utility.Settings;
import com.blackrook.utility.Utility;
import com.blackrook.utility.Version;

/**
 * Utility that merges WADs together.
 * @author Matthew Tropiano
 */
public class DoomMerge extends Utility<DoomMerge.MergeContext>
{
	private static final Version VERSION = new Version(1,1,0,0);
	
	/**
	 * Program context.
	 */
	public static class MergeContext implements Context
	{
		/** Print Output. */
		PrintStream out;
		/** List of input files. */
		List<File> inputFiles;
		/** Current output. */
		WadBuffer outWad;

		MergeContext(PrintStream out)
		{
			this.out = out;
			outWad = new WadBuffer();
			inputFiles = new List<File>();
		}
	}

	/**
	 * List of Doom Merge commands.
	 */
	private static enum MergeCommand implements Command<MergeContext>
	{
		/** 
		 * Echoes text to STDOUT.
		 * ARGS are printed as though it were a line of text.
		 */
		ECHO
		{
			@Override
			public boolean execute(MergeContext context, String ... args)
			{
				StringBuilder sb = new StringBuilder();
				for (int i = 0; i < args.length; i++)
				{
					sb.append(args[i]);
					if (i < args.length - 1)
						sb.append(' ');
				}
					
				context.out.println(sb.toString());
				return true;
			}
		},

		/** 
		 * Terminates the program.
		 */
		END
		{
			@Override
			public boolean execute(MergeContext context, String ... args)
			{
				System.exit(0);
				return true;
			}
		},

		/** 
		 * Adds an input file.
		 * ARG0 is file path. 
		 */
		IN
		{
			@Override
			public boolean execute(MergeContext context, String ... args)
			{
				context.inputFiles.add(new File(args[0]));
				return true;
			}
		},
		
		/** 
		 * Clears input file list.
		 * Takes no args. 
		 */
		CLEARIN
		{
			@Override
			public boolean execute(MergeContext context, String ... args)
			{
				context.inputFiles.clear();
				return true;
			}
		},
		
		/**
		 * Creates a new output buffer.
		 */
		CLEAROUT
		{
			@Override
			public boolean execute(MergeContext context, String ... args)
			{
				context.outWad = new WadBuffer();
				return true;
			}
		},
		
		/**
		 * Opens an existing file into the output buffer, 
		 * replacing the contents of the buffer.
		 * ARG0 is file path.
		 */
		LOAD
		{
			@Override
			public boolean execute(MergeContext context, String ... args)
			{
				try {
					context.outWad = new WadBuffer(new File(args[0]));
				} catch (IOException e) {
					context.out.printf("ERROR: Could not open output WAD %s.", args[0]);
					return false;
				}
				return true;
			}
		},
		
		/**
		 * Saves the contents of the current buffer to the output file.
		 */
		SAVE
		{
			@Override
			public boolean execute(MergeContext context, String ... args)
			{
				try {
					context.outWad.writeToFile(new File(args[0]));
				} catch (IOException e) {
					context.out.printf("ERROR: %s: %s", e.getClass().getName(), e.getLocalizedMessage());
					return false;
				}
				return true;
			}
		},
		
		/**
		 * Adds a blank marker entry to the output Wad buffer.
		 */
		MARKER
		{
			@Override
			public boolean execute(MergeContext context, String ... args)
			{
				if (context.outWad == null)
				{
					context.out.println("ERROR: No output file!");
					return false;
				}

				try {
					context.outWad.addMarker(args[0]);
				} catch (IOException e) {
					context.out.printf("ERROR: Could not create marker entry %s.", args[0]);
					return false;
				}

				return true;
			}
		},
			
		/**
		 * Adds the contents of all of the "in" files to the output buffer.
		 */
		MERGE
		{
			@Override
			public boolean execute(MergeContext context, String ... args)
			{
				if (context.outWad == null)
				{
					context.out.println("ERROR: No output file!");
					return false;
				}

				try {
					for (File f : context.inputFiles)
					{
						if (f.isDirectory())
						{
							File[] files = f.listFiles();
							Arrays.sort(files);
							
							for (File dirfile : files)
							{
								if (dirfile.getName().startsWith(".") || dirfile.isHidden() || dirfile.isDirectory())
									continue;
								
								String entryName = dirfile.getName().toUpperCase();
								int extIndex = entryName.lastIndexOf('.');
								entryName = extIndex >= 0 
									? entryName.substring(0, Math.min(8, extIndex)) 
									: entryName.substring(0, Math.min(8, entryName.length()));
								
								ByteArrayOutputStream bos = new ByteArrayOutputStream();
								FileInputStream fis = null;
								try {
									fis = new FileInputStream(dirfile);
									Common.relay(fis, bos);
								} catch (IOException e) {
									context.out.println("ERROR: Cannot import directory file "+dirfile.getPath());
								} finally {
									Common.close(fis);
								}
								context.outWad.addData(entryName, bos.toByteArray());
							}
						}
						else
						{
							WadFile wad = new WadFile(f);
							for (int i = 0; i < wad.getSize(); i++)
							{
								WadEntry entry = wad.getEntry(i);
								context.outWad.addData(entry.getName(), wad.getData(entry));
							}
							wad.close();
						}
					}
				} catch (IOException e) {
					context.out.printf("ERROR: %s: %s", e.getClass().getName(), e.getLocalizedMessage());
					return false;
				}
				
				return true;
			}
		},
			
		/* END COMMANDS */
		;
		
	}
	
	@Override
	public Version getVersion()
	{
		return VERSION;
	}

	@Override
	public Settings getSettingsFromCMDLINE(String... args)
	{
		// does not use command line.
		return new Settings();
	}

	@Override
	public MergeContext createNewContext()
	{
		return new MergeContext(out);
	}

	@Override
	public int execute(MergeContext context, Settings settings)
	{
		out.printf("Doom Merge v%s by Matt Tropiano\n", getVersion());
		try {
			Executor<MergeContext, MergeCommand> executor = 
					new Executor<MergeContext, MergeCommand>(MergeCommand.class);
			executor.execute(System.in, context);
		} catch (ScriptException e) {
			System.err.println("ERROR: "+e.asErrorString());
			return 2;
		} catch (Exception e) {
			System.err.println("ERROR: "+e.getMessage());
			return 1;
		}
		return 0;
	}
}
