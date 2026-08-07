/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.source.formatter.check;

import com.liferay.petra.string.StringBundler;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.source.formatter.parser.JavaClass;
import com.liferay.source.formatter.parser.JavaClassParser;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Micaelle Silva
 */
public class UpgradeJavaPortletProviderCheck extends BaseUpgradeCheck {

	@Override
	protected String format(
			String fileName, String absolutePath, String content)
		throws Exception {

		JavaClass javaClass = JavaClassParser.parseJavaClass(fileName, content);

		List<String> implementedClassNames =
			javaClass.getImplementedClassNames();

		if (implementedClassNames.isEmpty() ||
			!_deprecatedInterfaceNames.containsAll(implementedClassNames)) {

			return content;
		}

		String newContent = _updateClassDeclaration(content);

		newContent = _removeOldImports(newContent, implementedClassNames);
		newContent = _updateComponentService(newContent, implementedClassNames);

		newContent = _addGetSupportedActionsMethod(
			newContent, implementedClassNames);

		return newContent;
	}

	@Override
	protected String[] getNewImports() {
		return new String[] {
			"com.liferay.portal.kernel.portlet.PortletProvider"
		};
	}

	private String _addGetSupportedActionsMethod(
		String content, List<String> implementedClassNames) {

		StringBundler actionsSB = new StringBundler(3);

		if (implementedClassNames.contains("EditPortletProvider")) {
			actionsSB.append("Action.EDIT, ");
		}

		if (implementedClassNames.contains("ManagePortletProvider")) {
			actionsSB.append("Action.MANAGE, ");
		}

		if (implementedClassNames.contains("ViewPortletProvider")) {
			actionsSB.append("Action.VIEW, ");
		}

		String actions = actionsSB.toString();

		actions = actions.substring(0, actions.length() - 2);

		int index = content.lastIndexOf("\n}");

		StringBundler sb = new StringBundler(5);

		sb.append("\n\t@Override\n\tpublic Action[] getSupportedActions() {\n");
		sb.append("\t\treturn _supportedActions;\n\t}\n\n");
		sb.append("\tprivate final Action[] _supportedActions = {\n\t\t");
		sb.append(actions);
		sb.append("\n\t};\n");

		return StringBundler.concat(
			content.substring(0, index), sb, "\n}",
			content.substring(index + 2));
	}

	private String _removeOldImports(
		String content, List<String> implementedClassNames) {

		for (String interfaceName : implementedClassNames) {
			content = StringUtil.removeSubstring(
				content,
				"import com.liferay.portal.kernel.portlet." + interfaceName +
					";\n");
		}

		return content;
	}

	private String _updateClassDeclaration(String content) {
		Matcher classDeclarationMatcher = _classDeclarationPattern.matcher(
			content);

		if (!classDeclarationMatcher.find()) {
			return content;
		}

		String className = classDeclarationMatcher.group(1);
		String extendsClassName = classDeclarationMatcher.group(2);

		StringBundler sb = new StringBundler(5);

		sb.append("class ");
		sb.append(className);

		if (extendsClassName != null) {
			sb.append(" extends ");
			sb.append(extendsClassName);
		}

		sb.append(" {");

		return content.substring(0, classDeclarationMatcher.start()) + sb +
			content.substring(classDeclarationMatcher.end());
	}

	private String _updateComponentService(
		String content, List<String> implementedClassNames) {

		Matcher serviceArrayMatcher = _serviceArrayPattern.matcher(content);

		if (serviceArrayMatcher.find()) {
			String[] serviceClassNames = serviceArrayMatcher.group(
				1
			).split(
				","
			);

			for (int i = 0; i < serviceClassNames.length; i++) {
				serviceClassNames[i] = StringUtil.removeSubstring(
					StringUtil.trim(serviceClassNames[i]), ".class");
			}

			Set<String> serviceClassNamesSet = new HashSet<>(
				Arrays.asList(serviceClassNames));

			if (_deprecatedInterfaceNames.containsAll(serviceClassNamesSet) &&
				serviceClassNamesSet.containsAll(implementedClassNames)) {

				return StringUtil.replaceFirst(
					content, serviceArrayMatcher.group(),
					"service = PortletProvider.class");
			}

			return content;
		}

		Matcher serviceSingleMatcher = _serviceSinglePattern.matcher(content);

		if (serviceSingleMatcher.find() &&
			_deprecatedInterfaceNames.contains(serviceSingleMatcher.group(1))) {

			return StringUtil.replaceFirst(
				content, serviceSingleMatcher.group(),
				"service = PortletProvider.class");
		}

		return content;
	}

	private static final Pattern _classDeclarationPattern = Pattern.compile(
		"class\\s+(\\w+)(?:\\s+extends\\s+([\\w.]+))?\\s+implements\\s+" +
			"[^{]+\\{");
	private static final Set<String> _deprecatedInterfaceNames = new HashSet<>(
		Arrays.asList(
			"EditPortletProvider", "ManagePortletProvider",
			"ViewPortletProvider"));
	private static final Pattern _serviceArrayPattern = Pattern.compile(
		"service\\s*=\\s*\\{([^}]*)}");
	private static final Pattern _serviceSinglePattern = Pattern.compile(
		"service\\s*=\\s*(\\w+)\\.class");

}