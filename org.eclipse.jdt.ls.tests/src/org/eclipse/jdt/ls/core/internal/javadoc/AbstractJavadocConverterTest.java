/*******************************************************************************
 * Copyright (c) 2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.javadoc;

import java.io.IOException;

public abstract class AbstractJavadocConverterTest {

	/**
	 * This Javadoc contains some <code> code </code>, a link to
	 * {@link IOException} and a table
	 * <table>
	 * <thead>
	 * <tr>
	 * <th>header 1</th>
	 * <th>header 2</th>
	 * </tr>
	 * </thead> <tbody>
	 * <tr>
	 * <td>data 1</td>
	 * <td>data 2</td>
	 * </tr>
	 * </tbody>
	 * </table>
	 * <br>
	 * {@literal <b>literal</b>} and now a list:
	 * <ul><li><b>Coffee</b>
	 * <ul>
	 * <li>Mocha</li>
	 * <li>Latte</li>
	 * </ul>
	 * </li>
	 * <li>Tea
	 * <ul>
	 * <li>Darjeeling</li>
	 * <li>Early Grey</li>
	 * </ul>
	 * </li>
	 * </ul>
	 * <ul>
	 *
	 * @param param1
	 *            the first parameter
	 * @param param2
	 *            the 2nd parameter
	 * @param param3
	 * @since 1.0
	 * @since .0
	 * @author <a href=\"mailto:foo@bar.com\">Ralf</a>
	 * @author <a href=\"mailto:bar@foo.com\">Andrew</a>
	 * @exception NastyException
	 *                a\n nasty exception
	 * @throws IOException
	 *             another nasty exception
	 * @return some kind of result
	 * @unknown unknown tag
	 * @unknown another unknown tag
	 */
	//@formatter:off
	static final String RAW_JAVADOC_0 =
			"This Javadoc  contains some <code> code </code>, a link to {@link IOException} and a table \n" +
			"<table>\n" +
			"  <thead><tr><th>header 1</th><th>header 2</th></tr></thead>\n" +
			"  <tbody><tr><td>data 1</td><td>data 2</td></tr></tbody>\n" +
			"  </table>\n"+
			"<br> literally {@literal <b>literal</b>} and now a list:\n"+
			"  <ul>"
			+ "<li><b>Coffee</b>" +
			"   <ul>" +
			"    <li>Mocha</li>" +
			"    <li>Latte</li>" +
			"   </ul>" +
			"  </li>" +
			"  <li>Tea" +
			"   <ul>" +
			"    <li>Darjeeling</li>" +
			"    <li>Early Grey</li>" +
			"   </ul>" +
			"  </li>" +
			"</ul>"+
			"\n"+
			" @param param1 the first parameter\n" +
			" @param param2 \n"+
			" the 2nd parameter\n" +
			" @param param3 \n"+
			" @since 1.0\n" +
			" @since .0\n" +
			" @author <a href=\"mailto:foo@bar.com\">Ralf</a>\n" +
			" @author <a href=\"mailto:bar@foo.com\">Andrew</a>\n" +
			" @exception NastyException a\n nasty exception\n" +
			" @throws \n"+
			"IOException another nasty exception\n" +
			" @return some kind of result\n"+
			" @unknown unknown tag\n"+
			" @unknown another unknown tag\n";

	//Not using a THEAD tag
		static final String RAW_JAVADOC_TABLE_0=
				"<table>\n" +
				"    <tr>\n" +
				"        <th>Header 1</th>\n" +
				"        <th>Header 2</th>\n" +
				"    </tr>\n" +
				"    <tr>\n" +
				"        <td>Row 1A</td>\n" +
				"        <td>Row 1B</td>\n" +
				"    </tr>\n" +
				"    <tr>\n" +
				"        <td>Row 2A</td>\n" +
				"        <td>Row 2B</td>\n" +
				"    </tr>\n" +
				"</table>";

	//Not using a THEAD tag
			static final String RAW_JAVADOC_TABLE_1=
					"<table>\n" +
					"    <tr>\n" +
					"        <td>Row 0A</td>\n" +
					"        <td>Row 0B</td>\n" +
					"    </tr>\n" +
					"    <tr>\n" +
					"        <td>Row 1A</td>\n" +
					"        <td>Row 1B</td>\n" +
					"    </tr>\n" +
					"    <tr>\n" +
					"        <td>Row 2A</td>\n" +
					"        <td>Row 2B</td>\n" +
					"    </tr>\n" +
					"</table>";

	//@formatter:off

}
