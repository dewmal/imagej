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

package imagej.updater.core;

import static imagej.updater.core.FilesCollection.DEFAULT_UPDATE_SITE;
import static imagej.updater.core.UpdaterTestUtils.addUpdateSite;
import static imagej.updater.core.UpdaterTestUtils.assertStatus;
import static imagej.updater.core.UpdaterTestUtils.cleanup;
import static imagej.updater.core.UpdaterTestUtils.initialize;
import static imagej.updater.core.UpdaterTestUtils.main;
import static imagej.updater.core.UpdaterTestUtils.writeFile;
import static org.junit.Assert.assertTrue;
import static org.scijava.util.FileUtils.deleteRecursively;
import imagej.updater.core.FileObject.Status;
import imagej.updater.util.StderrProgress;

import java.io.File;

import org.junit.After;
import org.junit.Test;

/**
 * Tests the command-line updater.
 * 
 * @author Johannes Schindelin
 */
public class CommandLineUpdaterTest {
	protected FilesCollection files;
	protected StderrProgress progress = new StderrProgress();

	@After
	public void after() {
		if (files != null) cleanup(files);
	}

	@Test
	public void testUploadCompleteSite() throws Exception {
		final String to_remove = "macros/to_remove.ijm";
		final String modified = "macros/modified.ijm";
		final String installed = "macros/installed.ijm";
		final String new_file = "macros/new_file.ijm";
		files = initialize(to_remove, modified, installed);

		File ijRoot = files.prefix("");
		writeFile(new File(ijRoot, modified), "Zing! Zing a zong!");
		writeFile(new File(ijRoot, new_file), "Aitch!");
		assertTrue(new File(ijRoot, to_remove).delete());

		files = main(files, "upload-complete-site", FilesCollection.DEFAULT_UPDATE_SITE);

		assertStatus(Status.OBSOLETE_UNINSTALLED, files, to_remove);
		assertStatus(Status.INSTALLED, files, modified);
		assertStatus(Status.INSTALLED, files, installed);
		assertStatus(Status.INSTALLED, files, new_file);
	}

	@Test
	public void testUpload() throws Exception {
		files = initialize();

		final String path = "macros/test.ijm";
		final File file = files.prefix(path);
		writeFile(file, "// test");
		files = main(files, "upload", "--update-site", DEFAULT_UPDATE_SITE, path);

		assertStatus(Status.INSTALLED, files, path);

		assertTrue(file.delete());
		files = main(files, "upload", path);

		assertStatus(Status.OBSOLETE_UNINSTALLED, files, path);
	}

	@Test
	public void testUploadCompleteSiteWithShadow() throws Exception {
		final String path = "macros/test.ijm";
		final String obsolete = "macros/obsolete.ijm";
		files = initialize(path, obsolete);

		assertTrue(files.prefix(obsolete).delete());
		files = main(files, "upload", obsolete);

		final File tmp = addUpdateSite(files, "second");
		writeFile(files.prefix(path), "// shadowing");
		writeFile(files.prefix(obsolete), obsolete);
		files = main(files, "upload-complete-site", "--force-shadow", "second");

		assertStatus(Status.INSTALLED, files, path);
		assertStatus(Status.INSTALLED, files, obsolete);
		files = main(files, "remove-update-site", "second");

		assertStatus(Status.MODIFIED, files, path);
		assertStatus(Status.OBSOLETE, files, obsolete);

		assertTrue(deleteRecursively(tmp));
	}
}

