package fr.obeo.dsl.arduino.utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Scanner;

import org.eclipse.acceleo.common.preference.AcceleoPreferences;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.emf.common.util.BasicMonitor;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.sirius.business.api.session.Session;
import org.eclipse.sirius.business.api.session.SessionManager;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;

import fr.obeo.dsl.arduino.Instruction;
import fr.obeo.dsl.arduino.Project;
import fr.obeo.dsl.arduino.Sketch;
import fr.obeo.dsl.arduino.build.ArduinoBuilder;
import fr.obeo.dsl.arduino.gen.main.Generate;
import fr.obeo.dsl.arduino.menus.ArduinoUiActivator;
import fr.obeo.dsl.arduino.preferences.ArduinoPreferences;
import fr.obeo.dsl.arduino.preferences.ArduinoSdkDialog;

public class ArduinoServices {
	private ArduinoPreferences preferences = new ArduinoPreferences();

	public IProject getWorkspaceProject() {
		IProject[] projects = ResourcesPlugin.getWorkspace().getRoot()
				.getProjects();

		if (projects.length > 0) {
			return projects[0];
		}
		return null;
	}

	public Session getSession() {
		Collection<Session> sessions = SessionManager.INSTANCE.getSessions();
		if (sessions.size() > 0) {
			return (Session) sessions.toArray()[0];
		}
		return null;
	}

	public Project getArduinoProject() {
		Session session = getSession();
		if (session == null) {
			return null;
		}
		Collection<Resource> resources = session.getSemanticResources();
		if (resources.size() == 0) {
			return null;
		}
		Resource resource = (Resource) resources.toArray()[0];
		return (Project) resource.getContents().get(0);
	}

	public boolean isInvalidSketch(Sketch sketch) {
		return !isValidSketch(sketch);
	}

	public boolean isValidSketch(Sketch sketch) {

		if (sketch != null) {
			Instruction instruction = sketch;
			while (instruction != null && instruction.getNext() != null
					&& !(instruction.getNext() instanceof Sketch)) {
				instruction = instruction.getNext();
			}

			if (instruction != null && instruction.getNext() != null
					&& instruction.getNext() instanceof Sketch) {
				return true;
			}
		}
		return false;
	}

	public void upload(final Sketch sketch) {
		if (preferences.getArduinoSdk() == null
				|| preferences.getArduinoSdk().length() == 0) {
			askUser();
			return;
		}
		ProgressMonitorDialog dialog = new ProgressMonitorDialog(PlatformUI
				.getWorkbench().getActiveWorkbenchWindow().getShell());
		try {
			dialog.run(true, true, new IRunnableWithProgress() {
				@Override
				public void run(IProgressMonitor monitor) {
					monitor.beginTask("Upload sketch to arduino platform...",
							100);
					monitor.subTask("Generate code");
					File genFolder = generateCode(sketch);
					monitor.worked(33);
					monitor.subTask("Compile code");

					String arduinoSdk = preferences.getArduinoSdk();
					String boardTag = sketch.getHardware().getPlatforms().get(0).getName();
					String workingDirectory = genFolder.toString();
					ArduinoBuilder builder = new ArduinoBuilder(arduinoSdk,
							boardTag, workingDirectory);
					builder.compile("Sketch", null);

					monitor.worked(33);
					monitor.subTask("Upload code");
					builder.upload();
					monitor.done();
				}
			});
		} catch (InvocationTargetException e) {
			ArduinoUiActivator.log(Status.ERROR, "Upload failed", e);
		} catch (InterruptedException e) {
			ArduinoUiActivator.log(Status.ERROR, "Upload failed", e);
		}

	}
	
	private void askUser() {
		Shell shell = PlatformUI.getWorkbench().getModalDialogShellProvider()
				.getShell();

		ArduinoSdkDialog dialog = new ArduinoSdkDialog(shell);
		dialog.open();
	}

	private File generateCode(Sketch sketch) {
		boolean oldNotificationsPref = AcceleoPreferences
				.areNotificationsForcedDisabled();
		AcceleoPreferences.switchForceDeactivationNotifications(true);
		IFile file = ResourcesPlugin
				.getWorkspace()
				.getRoot()
				.getFile(
						new Path(sketch.eResource().getURI()
								.toPlatformString(true)));
		IFolder folder = file.getProject().getFolder("code");
		File genFolder = folder.getRawLocation().makeAbsolute().toFile();

		try {
			Generate generator = new Generate(sketch.eResource().getURI(),
					genFolder, new ArrayList<Object>());
			generator.doGenerate(new BasicMonitor());
		} catch (IOException e) {
			ArduinoUiActivator.log(Status.ERROR, "Code generation failed", e);
		}
		AcceleoPreferences
				.switchForceDeactivationNotifications(oldNotificationsPref);

		return genFolder;
	}
}