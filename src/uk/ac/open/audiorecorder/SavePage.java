/*
Copyright 2009 The Open University
http://www.open.ac.uk/lts/projects/audioapplets/

This file is part of the "Open University audio applets" project.

The "Open University audio applets" project is free software: you can
redistribute it and/or modify it under the terms of the GNU General Public
License as published by the Free Software Foundation, either version 3 of the
License, or (at your option) any later version.

The "Open University audio applets" project is distributed in the hope that it
will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with the "Open University audio applets" project.
If not, see <http://www.gnu.org/licenses/>.
*/
package uk.ac.open.audiorecorder;

import java.awt.*;
import java.awt.event.*;
import java.io.*;

import javax.swing.*;
import javax.swing.event.*;

/** Save to disk page of applet. */
public class SavePage extends PageBase
{
	private static final long serialVersionUID=1L;

	private final static Color ERROR=Color.RED;

	private JTextField saveName;

	private JButton saveButton;

	/**
	 * Class has information about per-platform target location.
	 */
	public static class LocationInfo
	{
		private File targetFolder;
		private String explanation,horribleWarning;
		/** @return Target folder */
		File getTargetFolder()
		{
			return targetFolder;
		}
		/** @return Explanation */
		String getExplanation()
		{
			return explanation;
		}
		/** @return Horrible warning, if necessary */
		String getHorribleWarning()
		{
			return horribleWarning;
		}
		
		private static boolean doneTestSave, testSaveOk;
		
		private boolean testSave()
		{
			if(!doneTestSave)
			{
				// Test save
				try
				{
					File test = new File(
						targetFolder, "audiorecorder." + Math.random() + ".tmp");
					new FileOutputStream(test).close();
					test.delete();
					testSaveOk = true;
				}
				catch(IOException e)
				{
					testSaveOk = false;
				}
				doneTestSave = true;
			}
			return testSaveOk;
		}
		
		/** Constructs the information based on current platform. */
		LocationInfo()
		{
			String platform=System.getProperty("os.name");
			if(platform.indexOf("Mac")!=-1 ||
				  platform.indexOf("Windows")!=-1)
			{
				targetFolder=new File(System.getProperty("user.home")+"/Desktop");
				explanation="onto your desktop";
				horribleWarning=null;
			}
			else
			{
				targetFolder=new File(System.getProperty("user.home"));
				explanation="into your home directory ("+targetFolder+")";
				horribleWarning=null;
			}

			if(!testSave())
			{
				targetFolder=new File(System.getProperty("java.io.tmpdir"));
				explanation="into a temporary folder ("+targetFolder+")";
				horribleWarning="Please retrieve the file immediately from the " +
					"temporary folder as it may later be deleted by the system. You " +
					"should move it somewhere else, such as your desktop. To " +
					"access the temporary folder, click the Start button (Windows logo), " +
					"then type in the following: "+targetFolder+"\n\n"+
					"We apologise for the inconvenience; with your current operating " +
					"system and Java versions and permissions, it is not possible to save " +
					"the file somewhere more sensible.";
			}
		}
	}

	/** @param owner Owning panel */
	public SavePage(MainPanel owner)
	{
		super(owner,5,"Save your file");
		initButtons(null,null,null,0);
		enableButtons(false, false, false);

		final LocationInfo li=new LocationInfo();

		getMain().add(new WrappedText("The file will now be saved " +
			li.getExplanation()+
			". Please type a name for the file, not including .wav, "+
			"and then click Save."),
			BorderLayout.NORTH);

		JPanel lower=new JPanel(new BorderLayout(0,8));
		lower.setOpaque(false);
		getMain().add(lower,BorderLayout.CENTER);

		JPanel anotherBloodyPanel=new JPanel(new BorderLayout());
		anotherBloodyPanel.setOpaque(false);
		lower.add(anotherBloodyPanel,BorderLayout.NORTH);

		lower.add(new WrappedText("You cannot save files if they include spaces or " +
			"special characters, or a file of that name already exists."),BorderLayout.CENTER);

		JPanel savePanel=new JPanel(new BorderLayout(8,0));
		savePanel.setOpaque(false);
		anotherBloodyPanel.add(savePanel,BorderLayout.WEST);

		saveName=new JTextField(20);
		JLabel dotWav = new FLabel(FontType.NORMAL, ".wav");
		dotWav.setFont(dotWav.getFont().deriveFont(Font.PLAIN));
		JPanel boxPanel=new JPanel(new BorderLayout(2,0));
		boxPanel.setOpaque(false);
		boxPanel.add(saveName,BorderLayout.WEST);
		boxPanel.add(dotWav,BorderLayout.EAST);

		saveButton = new FButton("Save");
		saveButton.setEnabled(false);
		savePanel.add(boxPanel,BorderLayout.WEST);
		savePanel.add(saveButton,BorderLayout.EAST);

		final Color normalForeground=saveName.getForeground();

		saveName.getDocument().addDocumentListener(new DocumentListener()
		{
			public void changedUpdate(DocumentEvent e)
			{
				String filename=saveName.getText();
				File target=new File(li.getTargetFolder(),filename+".wav");
				if(filename.matches("[a-zA-Z0-9-_.]+") && !target.exists())
				{
					saveName.setForeground(normalForeground);
					saveButton.setEnabled(true);
				}
				else
				{
					saveName.setForeground(ERROR);
					saveButton.setEnabled(false);
				}
			}

			public void insertUpdate(DocumentEvent e)
			{
				changedUpdate(e);
			}

			public void removeUpdate(DocumentEvent e)
			{
				changedUpdate(e);
			}
		});

		saveButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent ev)
			{
				String filename=saveName.getText();
				File target=new File(li.getTargetFolder(),filename+".wav");
				try
				{
					getRecording().save(target);
					getOwner().setPage(MainPanel.PAGE_FINISHED);
				}
				catch(Throwable t)
				{
					getOwner().showError(t);
				}
			}
		});

	}

	@Override
	protected void enter()
	{
		super.enter();
		saveName.requestFocus();
		getRootPane().setDefaultButton(saveButton);
	}
}
