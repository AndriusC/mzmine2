/*
 * Copyright 2006-2011 The MZmine 2 Development Team
 *
 * This file is part of MZmine 2.
 *
 * MZmine 2 is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * MZmine 2 is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * MZmine 2; if not, write to the Free Software Foundation, Inc., 51 Franklin
 * St, Fifth Floor, Boston, MA 02110-1301 USA
 */

package net.sf.mzmine.modules.projectmethods.projectclose;

import java.util.logging.Logger;

import javax.swing.JInternalFrame;
import javax.swing.JOptionPane;

import net.sf.mzmine.main.MZmineCore;
import net.sf.mzmine.modules.MZmineModuleCategory;
import net.sf.mzmine.modules.MZmineProcessingModule;
import net.sf.mzmine.parameters.ParameterSet;
import net.sf.mzmine.project.MZmineProject;
import net.sf.mzmine.project.ProjectManager;
import net.sf.mzmine.project.impl.MZmineProjectImpl;
import net.sf.mzmine.taskcontrol.Task;

/**
 * This is a very simple module which adds the option to close a current project
 * 
 */
public class ProjectCloseModule implements MZmineProcessingModule {

	private Logger logger = Logger.getLogger(this.getClass().getName());

	public static final String MODULE_NAME = "Close project";

	public ParameterSet getParameterSet() {
		return null;
	}

	@Override
	public Task[] runModule(ParameterSet parameters) {
		int selectedValue = JOptionPane.showInternalConfirmDialog(MZmineCore
				.getDesktop().getMainFrame().getContentPane(),
				"Are you sure you want to close the current project?",
				"Close project", JOptionPane.YES_NO_OPTION,
				JOptionPane.WARNING_MESSAGE);

		if (selectedValue != JOptionPane.YES_OPTION)
			return null;

		// Close all open frames related to previous project
		JInternalFrame frames[] = MZmineCore.getDesktop().getInternalFrames();
		for (JInternalFrame frame : frames) {
			// Use doDefailtCloseAction() instead of dispose() to protect
			// the TaskProgressWindow from disposing
			frame.doDefaultCloseAction();
		}

		// Create a new, empty project
		MZmineProject newProject = new MZmineProjectImpl();

		// Replace the current project with the new one
		ProjectManager projectManager = MZmineCore.getProjectManager();
		projectManager.setCurrentProject(newProject);

		// Ask the garbage collector to free the previously used memory
		System.gc();

		logger.info("Project closed.");
		return null;
	}

	@Override
	public MZmineModuleCategory getModuleCategory() {
		return MZmineModuleCategory.PROJECTIO;
	}

	public String toString() {
		return MODULE_NAME;
	}

}
