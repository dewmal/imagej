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

package imagej.legacy;

import javassist.CannotCompileException;
import javassist.ClassClassPath;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.CtNewMethod;
import javassist.Modifier;
import javassist.NotFoundException;

/**
 * The code hacker provides a mechanism for altering the behavior of classes
 * before they are loaded, for the purpose of injecting new methods and/or
 * altering existing ones.
 * <p>
 * In ImageJ, this mechanism is used to provide new seams into legacy ImageJ1
 * code, so that (e.g.) the modern UI is aware of legacy ImageJ events as they
 * occur.
 * </p>
 * 
 * @author Curtis Rueden
 * @author Rick Lentz
 */
public class CodeHacker {

	private static final String PATCH_PKG = "imagej.legacy.patches";
	private static final String PATCH_SUFFIX = "Methods";

	private final ClassPool pool;
	private final ClassLoader classLoader;

	public CodeHacker(ClassLoader classLoader) {
		this.classLoader = classLoader;
		pool = ClassPool.getDefault();
		pool.appendClassPath(new ClassClassPath(getClass()));
	}

	/**
	 * Modifies a class by injecting additional code at the end of the specified
	 * method's body.
	 * <p>
	 * The extra code is defined in the imagej.legacy.patches package, as
	 * described in the documentation for {@link #insertMethod(String, String)}.
	 * </p>
	 * 
	 * @param fullClass Fully qualified name of the class to modify.
	 * @param methodSig Method signature of the method to modify; e.g.,
	 *          "public void updateAndDraw()"
	 */
	public void
		insertAfterMethod(final String fullClass, final String methodSig)
	{
		insertAfterMethod(fullClass, methodSig, newCode(fullClass, methodSig));
	}

	/**
	 * Modifies a class by injecting the provided code string at the end of the
	 * specified method's body.
	 * 
	 * @param fullClass Fully qualified name of the class to modify.
	 * @param methodSig Method signature of the method to modify; e.g.,
	 *          "public void updateAndDraw()"
	 * @param newCode The string of code to add; e.g., System.out.println(\"Hello
	 *          World!\");
	 */
	public void insertAfterMethod(final String fullClass,
		final String methodSig, final String newCode)
	{
		try {
			getMethod(fullClass, methodSig).insertAfter(expand(newCode));
		}
		catch (final CannotCompileException e) {
			throw new IllegalArgumentException("Cannot modify method: " + methodSig,
				e);
		}
	}

	/**
	 * Modifies a class by injecting additional code at the start of the specified
	 * method's body.
	 * <p>
	 * The extra code is defined in the imagej.legacy.patches package, as
	 * described in the documentation for {@link #insertMethod(String, String)}.
	 * </p>
	 * 
	 * @param fullClass Fully qualified name of the class to override.
	 * @param methodSig Method signature of the method to override; e.g.,
	 *          "public void updateAndDraw()"
	 */
	public void
		insertBeforeMethod(final String fullClass, final String methodSig)
	{
		insertBeforeMethod(fullClass, methodSig, newCode(fullClass, methodSig));
	}

	/**
	 * Modifies a class by injecting the provided code string at the start of the
	 * specified method's body.
	 * 
	 * @param fullClass Fully qualified name of the class to override.
	 * @param methodSig Method signature of the method to override; e.g.,
	 *          "public void updateAndDraw()"
	 * @param newCode The string of code to add; e.g., System.out.println(\"Hello
	 *          World!\");
	 */
	public void insertBeforeMethod(final String fullClass,
		final String methodSig, final String newCode)
	{
		try {
			getMethod(fullClass, methodSig).insertBefore(expand(newCode));
		}
		catch (final CannotCompileException e) {
			throw new IllegalArgumentException("Cannot modify method: " + methodSig,
				e);
		}
	}

	/**
	 * Modifies a class by injecting a new method.
	 * <p>
	 * The body of the method is defined in the imagej.legacy.patches package, as
	 * described in the {@link #insertMethod(String, String)} method
	 * documentation.
	 * <p>
	 * The new method implementation should be declared in the
	 * imagej.legacy.patches package, with the same name as the original class
	 * plus "Methods"; e.g., overridden ij.gui.ImageWindow methods should be
	 * placed in the imagej.legacy.patches.ImageWindowMethods class.
	 * </p>
	 * <p>
	 * New method implementations must be public static, with an additional first
	 * parameter: the instance of the class on which to operate.
	 * </p>
	 * 
	 * @param fullClass Fully qualified name of the class to override.
	 * @param methodSig Method signature of the method to override; e.g.,
	 *          "public void setVisible(boolean vis)"
	 */
	public void insertMethod(final String fullClass, final String methodSig) {
		insertMethod(fullClass, methodSig, newCode(fullClass, methodSig));
	}

	/**
	 * Modifies a class by injecting the provided code string as a new method.
	 * 
	 * @param fullClass Fully qualified name of the class to override.
	 * @param methodSig Method signature of the method to override; e.g.,
	 *          "public void updateAndDraw()"
	 * @param newCode The string of code to add; e.g., System.out.println(\"Hello
	 *          World!\");
	 */
	public void insertMethod(final String fullClass, final String methodSig,
		final String newCode)
	{
		final CtClass classRef = getClass(fullClass);
		final String methodBody = methodSig + " { " + expand(newCode) + " } ";
		try {
			final CtMethod methodRef = CtNewMethod.make(methodBody, classRef);
			classRef.addMethod(methodRef);
		}
		catch (final CannotCompileException e) {
			throw new IllegalArgumentException("Cannot add method: " + methodSig, e);
		}
	}

	/**
	 * Loads the given, possibly modified, class.
	 * <p>
	 * This method must be called to confirm any changes made with
	 * {@link #insertAfterMethod}, {@link #insertBeforeMethod},
	 * or {@link #insertMethod}.
	 * </p>
	 * 
	 * @param fullClass Fully qualified class name to load.
	 * @return the loaded class
	 */
	public Class<?> loadClass(final String fullClass) {
		final CtClass classRef = getClass(fullClass);
		try {
			return classRef.toClass(classLoader, null);
		}
		catch (final CannotCompileException e) {
			// Cannot use LogService; it will not be initialized by the time the DefaultLegacyService
			// class is loaded, which is when the CodeHacker is run
			System.err.println("Warning: Cannot load class: " + fullClass);
			e.printStackTrace();
			return null;
		}
	}

	/** Gets the Javassist class object corresponding to the given class name. */
	private CtClass getClass(final String fullClass) {
		try {
			return pool.get(fullClass);
		}
		catch (final NotFoundException e) {
			throw new IllegalArgumentException("No such class: " + fullClass, e);
		}
	}

	/**
	 * Gets the Javassist method object corresponding to the given method
	 * signature of the specified class name.
	 */
	private CtMethod getMethod(final String fullClass, final String methodSig) {
		final CtClass cc = getClass(fullClass);
		final String name = getMethodName(methodSig);
		final String[] argTypes = getMethodArgTypes(methodSig);
		final CtClass[] params = new CtClass[argTypes.length];
		for (int i = 0; i < params.length; i++) {
			params[i] = getClass(argTypes[i]);
		}
		try {
			return cc.getDeclaredMethod(name, params);
		}
		catch (final NotFoundException e) {
			throw new IllegalArgumentException("No such method: " + methodSig, e);
		}
	}

	/**
	 * Generates a new line of code calling the {@link imagej.legacy.patches}
	 * class and method corresponding to the given method signature.
	 */
	private String newCode(final String fullClass, final String methodSig) {
		final int dotIndex = fullClass.lastIndexOf(".");
		final String className = fullClass.substring(dotIndex + 1);

		final String methodName = getMethodName(methodSig);
		final boolean isStatic = isStatic(methodSig);
		final boolean isVoid = isVoid(methodSig);

		final String patchClass = PATCH_PKG + "." + className + PATCH_SUFFIX;
		for (final CtMethod method : getClass(patchClass).getMethods()) try {
			if ((method.getModifiers() & Modifier.STATIC) == 0) continue;
			final CtClass[] types = method.getParameterTypes();
			if (types.length == 0 || !types[0].getName().equals("imagej.legacy.LegacyService")) {
				throw new UnsupportedOperationException("Method " + method + " of class " + patchClass + " has wrong type!");
			}
		} catch (NotFoundException e) {
			e.printStackTrace();
		}

		final StringBuilder newCode =
			new StringBuilder((isVoid ? "" : "return ") + patchClass + "." + methodName + "(");
		newCode.append("$service");
		if (!isStatic) {
			newCode.append(", this");
		}
		final int argCount = getMethodArgTypes(methodSig).length;
		for (int i = 1; i <= argCount; i++) {
			newCode.append(", $" + i);
		}
		newCode.append(");");

		return newCode.toString();
	}

	/** Patches in the current legacy service for '$service' */
	private String expand(final String code) {
		return code
			.replace("$isLegacyMode()", "imagej.legacy.Utils.isLegacyMode($service)")
			.replace("$service", "imagej.legacy.DefaultLegacyService.getInstance()");
	}

	/** Extracts the method name from the given method signature. */
	private String getMethodName(final String methodSig) {
		final int parenIndex = methodSig.indexOf("(");
		final int spaceIndex = methodSig.lastIndexOf(" ", parenIndex);
		return methodSig.substring(spaceIndex + 1, parenIndex);
	}

	private String[] getMethodArgTypes(final String methodSig) {
		final int parenIndex = methodSig.indexOf("(");
		final String methodArgs =
			methodSig.substring(parenIndex + 1, methodSig.length() - 1);
		final String[] args =
			methodArgs.equals("") ? new String[0] : methodArgs.split(",");
		for (int i = 0; i < args.length; i++) {
			args[i] = args[i].trim().split(" ")[0];
		}
		return args;
	}

	/** Returns true if the given method signature is static. */
	private boolean isStatic(final String methodSig) {
		final int parenIndex = methodSig.indexOf("(");
		final String methodPrefix = methodSig.substring(0, parenIndex);
		for (final String token : methodPrefix.split(" ")) {
			if (token.equals("static")) return true;
		}
		return false;
	}

	/** Returns true if the given method signature returns void. */
	private boolean isVoid(final String methodSig) {
		final int parenIndex = methodSig.indexOf("(");
		final String methodPrefix = methodSig.substring(0, parenIndex);
		return methodPrefix.startsWith("void ") ||
			methodPrefix.indexOf(" void ") > 0;
	}

}
