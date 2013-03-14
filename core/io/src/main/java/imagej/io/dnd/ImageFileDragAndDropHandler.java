/*
 * #%L
 * ImageJ software for multidimensional image processing and analysis.
 * %%
 * Copyright (C) 2009 - 2013 Board of Regents of the University of
 * Wisconsin-Madison, Broad Institute of MIT and Harvard, and Max Planck
 * Institute of Molecular Cell Biology and Genetics.
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * 
 * The views and conclusions contained in the software and documentation are
 * those of the authors and should not be interpreted as representing official
 * policies, either expressed or implied, of any organization.
 * #L%
 */

package imagej.io.dnd;

import imagej.data.Dataset;
import imagej.display.Display;
import imagej.display.DisplayService;
import imagej.io.IOService;
import imagej.ui.dnd.AbstractDragAndDropHandler;
import imagej.ui.dnd.DragAndDropData;
import imagej.ui.dnd.DragAndDropHandler;

import java.io.File;

import net.imglib2.exception.IncompatibleTypeException;
import net.imglib2.io.ImgIOException;

import org.scijava.Priority;
import org.scijava.log.LogService;
import org.scijava.plugin.Plugin;

/**
 * Drag-and-drop handler for image files.
 * 
 * @author Curtis Rueden
 * @author Barry DeZonia
 */
@Plugin(type = DragAndDropHandler.class, priority = Priority.VERY_LOW_PRIORITY)
public class ImageFileDragAndDropHandler extends AbstractDragAndDropHandler {

	// -- constants --

	public static final String MIME_TYPE = "application/imagej-image";

	// -- DragAndDropHandler methods --

	@Override
	public boolean isCompatible(final Display<?> display, final Object data)
	{
		if (data instanceof File) {
			// TODO
			// Right now we've made this plugin a very low priority handler. So all
			// other handlers get first crack. If they can't handle we assume its an
			// image file and open it here.
			// I think we want to make the TextDragAndDropHandler act like this
			// instead. What we really need to do here is ask the IOService if the
			// File is an image.
			return true;
		}
		if (data instanceof DragAndDropData) {
			DragAndDropData dndData = (DragAndDropData) data;
			for (final String mimeType : dndData.getMimeTypes()) {
				if (MIME_TYPE.equals(mimeType)) return true;
			}
		}
		return false;
	}

	@Override
	public boolean drop(final Display<?> display, final Object data) {
		final IOService ioService = getContext().getService(IOService.class);
		final DisplayService displayService =
			getContext().getService(DisplayService.class);
		if (ioService == null || displayService == null) return false;

		final LogService log = getContext().getService(LogService.class);

		String filename = null;

		if (data instanceof File) {
			filename = ((File) data).getAbsolutePath();
		}

		if (data instanceof DragAndDropData) {
			DragAndDropData dndData = (DragAndDropData) data;
			filename = (String) dndData.getData(MIME_TYPE);
		}

		if (filename == null) return false;

		// load file
		boolean success = true;
		try {
			final Dataset dataset = ioService.loadDataset(filename);
			final Display<?> d = displayService.createDisplay(dataset);
			// HACK: update display a bit later - else data is not drawn correctly
			new Thread() {

				@Override
				public void run() {
					try {
						Thread.sleep(50);
					}
					catch (Exception e) {/**/}
					d.update();
				}
			}.start();
		}
		catch (final ImgIOException exc) {
			if (log != null) log.error("Error opening file: " + filename, exc);
			success = false;
		}
		catch (final IncompatibleTypeException exc) {
			if (log != null) log.error("Error opening file: " + filename, exc);
			success = false;
		}
		return success;
	}

}