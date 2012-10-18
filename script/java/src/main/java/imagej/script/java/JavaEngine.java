package imagej.script.java;

import imagej.build.minimaven.BuildEnvironment;
import imagej.build.minimaven.Coordinate;
import imagej.build.minimaven.MavenProject;
import imagej.script.AbstractScriptEngine;
import imagej.util.FileUtils;
import imagej.util.LineOutputStream;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.script.ScriptException;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

public class JavaEngine extends AbstractScriptEngine {

	private final static String DEFAULT_GROUP_ID = "net.imagej";
	private final static String DEFAULT_VERSION = "1.0.0-SNAPSHOT";

	private final static String XALAN_INDENT_AMOUNT = "{http://xml.apache.org/xslt}indent-amount";
	{
		engineScopeBindings = new JavaEngineBindings();
	}

	@Override
	public Object eval(String script) throws ScriptException {
		return eval(new StringReader(script));
	}

	@Override
	public Object eval(Reader reader) throws ScriptException {
		final Writer writer = getContext().getErrorWriter();
		final PrintStream err = writer == null ? System.err : new PrintStream(new LineOutputStream() {

			@Override
			public void println(final String line) throws IOException {
				writer.append(line).append('\n');
			}

		});

		try {
			BuildEnvironment env = new BuildEnvironment(err, true, true, false);
			final MavenProject project;
			String mainClass = null;

			final String path = (String)get(FILENAME);
			File file = path == null ? null : new File(path);
			if (file == null || !file.exists()) try {
				project = writeTemporaryProject(env, reader);
				mainClass = project.getMainClass();
			} catch (Exception e) {
				throw new ScriptException(e);
			} else {
				if (file.getName().equals("pom.xml")) {
					project = env.parse(file, null);
				} else {
					mainClass = getFullClassName(file);
					project = getMavenProject(env, file, mainClass);
				}
			}

			project.build(true);
			if (mainClass == null) {
				mainClass = project.getMainClass();
				if (mainClass == null) {
					throw new ScriptException("No main class found for file " + file);
				}
			}

			// make class loader
			String[] paths = project.getClassPath(false).split(File.pathSeparator);
			URL[] urls = new URL[paths.length];
			for (int i = 0; i < urls.length; i++)
				urls[i] = new URL("file:" + paths[i] + (paths[i].endsWith(".jar") ? "" : "/"));
			URLClassLoader classLoader = new URLClassLoader(urls);

			// needed for sezpoz
			Thread.currentThread().setContextClassLoader(classLoader);

			// launch main class
			Class<?> clazz = classLoader.loadClass(mainClass);
			Method main = clazz.getMethod("main", new Class[] { String[].class });
			main.invoke(null, new Object[] { new String[0] });
		} catch (Exception e) {
			throw new ScriptException(e);
		}
		return null;
	}

	private MavenProject getMavenProject(final BuildEnvironment env, final File file, final String mainClass) throws ScriptException, IOException, ParserConfigurationException, SAXException {
		String path = file.getAbsolutePath();
		if (!path.replace(File.separatorChar, '.').endsWith("." + mainClass + ".java")) {
			throw new ScriptException("Class " + mainClass + " in invalid directory: " + path);
		}
		path = path.substring(0, path.length() - mainClass.length() - 5);
		if (path.replace(File.separatorChar, '/').endsWith("/src/main/java/")) {
			path = path.substring(0, path.length() - "src/main/java/".length());
			final File pom = new File(path + "pom.xml");
			if (pom.exists()) return env.parse(pom, null);
		}
		throw new ScriptException("TODO: Generate pom.xml on the fly");
	}

	private static String getFullClassName(final File file) throws IOException {
		String name = file.getName();
		if (!name.endsWith(".java")) {
			throw new UnsupportedOperationException();
		}
		name = name.substring(0, name.length() - 5);

		String packageName = "";
		final Pattern packagePattern = Pattern.compile("package ([a-zA-Z0-9_.]*).*");
		final Pattern classPattern = Pattern.compile(".*public class ([a-zA-Z0-9_]*).*");
		final BufferedReader reader = new BufferedReader(new FileReader(file));
		for (;;) {
			String line = reader.readLine().trim();
			if (line == null) break;
			while (line.startsWith("/*")) {
				int end = line.indexOf("*/", 2);
				while (end < 0) {
						line = reader.readLine();
						if (line == null) return name;
						end = line.indexOf("*/");
				}
				line = line.substring(end + 2).trim();
			}
			if (line.equals("") || line.startsWith("//")) continue;
			final Matcher packageMatcher = packagePattern.matcher(line);
			if (packageMatcher.matches()) {
				packageName = packageMatcher.group(1) + ".";
			}
			final Matcher classMatcher = classPattern.matcher(line);
			if (classMatcher.matches()) {
				return packageName + classMatcher.group(1);
			}
		}
		return packageName + name; // the 'package' statement must be the first in the file
	}

	private static MavenProject writeTemporaryProject(final BuildEnvironment env,
		final Reader reader) throws IOException, ParserConfigurationException,
		SAXException, TransformerConfigurationException, TransformerException,
		TransformerFactoryConfigurationError
	{
		final File directory = FileUtils.createTemporaryDirectory("java", "");
		final File file = new File(directory, ".java");

		final BufferedReader in = new BufferedReader(reader);
		final Writer out = new FileWriter(file);
		for (;;) {
			final String line = in.readLine();
			if (line == null) break;
			out.write(line);
			out.write('\n');
		}
		in.close();
		out.close();

		final String mainClass = getFullClassName(file);
		final File result = new File(directory, "src/main/java/" + mainClass.replace('.', '/') + ".java");
		if (!result.getParentFile().mkdirs()) {
			throw new IOException("Could not make directory for " + result);
		}
		if (!file.renameTo(result)) {
			throw new IOException("Could not move " + file + " into the correct location");
		}

		// write POM
		return fakePOM(env, directory, fakeArtifactId(env, directory.getName()), mainClass);
	}

	private static String fakeArtifactId(final BuildEnvironment env, final String name) {
		int dot = name.indexOf('.');
		final String prefix = dot < 0 ? name : dot == 0 ? "dependency" : name.substring(0, dot);
		if (!env.containsProject(DEFAULT_GROUP_ID, prefix)) {
			return prefix;
		}
		for (int i = 1; ; i++) {
			final String artifactId = prefix + "-" + i;
			if (!env.containsProject(DEFAULT_GROUP_ID, artifactId)) {
				return artifactId;
			}
		}
	}

	private static MavenProject fakePOM(final BuildEnvironment env,
			final File directory, final String artifactId, final String mainClass)
					throws IOException, ParserConfigurationException, SAXException,
					TransformerConfigurationException, TransformerException,
					TransformerFactoryConfigurationError {
		final Document pom = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
		final Element project = pom.createElement("project");
		pom.appendChild(project);
		project.setAttribute("xmlns", "http://maven.apache.org/POM/4.0.0");
		project.setAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
		project.setAttribute("xsi:schemaLocation", "http://maven.apache.org/POM/4.0.0 " +
				"http://maven.apache.org/xsd/maven-4.0.0.xsd");

		append(pom, project, "groupId", DEFAULT_GROUP_ID);
		append(pom, project, "artifactId", artifactId);
		append(pom, project, "version", DEFAULT_VERSION);

		final Element build = append(pom, project, "build", null);
		append(pom, build, "sourceDirectory", directory.getPath());

		if (mainClass != null) {
			final Element plugins = append(pom, build, "plugins", null);
			final Element plugin = append(pom, plugins, "plugin", null);
			append(pom, plugin, "artifactId", "maven-jar-plugin");
			final Element configuration = append(pom, plugin, "configuration", null);
			final Element archive = append(pom, configuration, "archive", null);
			final Element manifest = append(pom, archive, "manifest", null);
			append(pom, manifest, "mainClass", mainClass);
		}

		Element dependencies = append(pom, project, "dependencies", null);
		for (Coordinate dependency : getAllDependencies(env)) {
			Element dep = append(pom, dependencies, "dependency", null);
			append(pom, dep, "groupId", dependency.getGroupId());
			append(pom, dep, "artifactId", dependency.getArtifactId());
			append(pom, dep, "version", dependency.getVersion());
		}
		final File pomFile = new File(directory, "pom.xml");
		final FileWriter writer = new FileWriter(pomFile);
		final Transformer transformer = TransformerFactory.newInstance().newTransformer();
		transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
		transformer.setOutputProperty(OutputKeys.INDENT, "yes");
		transformer.setOutputProperty(XALAN_INDENT_AMOUNT, "4");
		transformer.transform(new DOMSource(pom), new StreamResult(writer));
		return env.parse(pomFile);
	}

	private static Element append(final Document document, final Element parent, final String tag, final String content) {
		Element child = document.createElement(tag);
		if (content != null) child.appendChild(document.createCDATASection(content));
		parent.appendChild(child);
		return child;
	}

	private static List<Coordinate> getAllDependencies(final BuildEnvironment env) {
		final List<Coordinate> result = new ArrayList<Coordinate>();
		for (ClassLoader loader = env.getClass().getClassLoader(); loader != null; loader = loader.getParent()) {
			if (loader instanceof URLClassLoader) {
				for (final URL url : ((URLClassLoader)loader).getURLs()) {
					if (url.getProtocol().equals("file")) {
						final File file = new File(url.getPath());
						final String artifactId = fakeArtifactId(env, file.getName());
						Coordinate dependency = new Coordinate(DEFAULT_GROUP_ID, artifactId, "1.0.0");
						env.fakePOM(file, dependency);
						result.add(dependency);
					}
				}
			}
		}
		return result;
	}

}