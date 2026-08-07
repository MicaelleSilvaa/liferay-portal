/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.source.formatter.check;

import com.liferay.petra.string.StringBundler;
import com.liferay.portal.kernel.util.ListUtil;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.TreeMapBuilder;
import com.liferay.source.formatter.parser.JavaClass;
import com.liferay.source.formatter.parser.JavaClassParser;
import com.liferay.source.formatter.parser.JavaTerm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
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

		List<String> deprecatedClassNames = ListUtil.filter(
			javaClass.getImplementedClassNames(),
			_portletProviderActions::containsKey);

		if (deprecatedClassNames.isEmpty()) {
			return content;
		}

		String newContent = _updateClassDeclaration(content, javaClass);

		if (content.equals(newContent)) {
			return content;
		}

		newContent = _addSupportedActions(
			newContent, deprecatedClassNames, javaClass);
		newContent = _removeOldImports(newContent, deprecatedClassNames);
		newContent = _updateComponentService(newContent, javaClass);

		return newContent;
	}

	@Override
	protected String[] getNewImports() {
		return new String[] {
			"com.liferay.portal.kernel.portlet.PortletProvider"
		};
	}

	private String _addJavaTerm(
		String content, String javaTermContent, JavaTerm nextJavaTerm) {

		if (nextJavaTerm == null) {
			int index = content.lastIndexOf("\n}");

			return StringBundler.concat(
				content.substring(0, index), "\n", javaTermContent,
				content.substring(index));
		}

		int index = content.indexOf(nextJavaTerm.getContent());

		return StringBundler.concat(
			content.substring(0, index), javaTermContent, "\n",
			content.substring(index));
	}

	private String _addSupportedActions(
		String content, List<String> deprecatedClassNames,
		JavaClass javaClass) {

		for (JavaTerm javaTerm : javaClass.getChildJavaTerms()) {
			if (javaTerm.isJavaMethod() &&
				Objects.equals(javaTerm.getName(), "getSupportedActions")) {

				return content;
			}
		}

		List<String> actions = new ArrayList<>();

		for (Map.Entry<String, String> entry :
				_portletProviderActions.entrySet()) {

			if (deprecatedClassNames.contains(entry.getKey())) {
				actions.add(entry.getValue());
			}
		}

		String actionsString = StringUtil.merge(actions, ", ");

		StringBundler sb = new StringBundler(6);

		String declaration = "private final Action[] _supportedActions = {";

		sb.append("\t");
		sb.append(declaration);

		if ((4 + declaration.length() + actionsString.length() + 2) > 80) {
			sb.append("\n\t\t");
			sb.append(actionsString);
			sb.append("\n\t");
		}
		else {
			sb.append(actionsString);
		}

		sb.append("};\n");

		JavaTerm nextJavaMethod = null;
		JavaTerm nextJavaVariable = null;

		for (JavaTerm javaTerm : javaClass.getChildJavaTerms()) {
			String name = javaTerm.getName();

			if ((nextJavaMethod == null) &&
				(javaTerm.isJavaClass() ||
				 (javaTerm.isJavaMethod() &&
				  (!javaTerm.isPublic() ||
				   (name.compareTo("getSupportedActions") > 0))) ||
				 (javaTerm.isJavaVariable() && !javaTerm.isPublic()))) {

				nextJavaMethod = javaTerm;
			}

			if ((nextJavaVariable == null) &&
				(javaTerm.isJavaClass() ||
				 (javaTerm.isJavaVariable() && !javaTerm.isStatic() &&
				  (name.compareTo("_supportedActions") > 0)))) {

				nextJavaVariable = javaTerm;
			}
		}

		content = _addJavaTerm(
			content,
			"\t@Override\n\tpublic Action[] getSupportedActions() {\n" +
				"\t\treturn _supportedActions;\n\t}\n",
			nextJavaMethod);

		return _addJavaTerm(content, sb.toString(), nextJavaVariable);
	}

	private String _removeOldImports(
		String content, List<String> deprecatedClassNames) {

		for (String deprecatedClassName : deprecatedClassNames) {
			content = StringUtil.removeSubstring(
				content,
				"import com.liferay.portal.kernel.portlet." +
					deprecatedClassName + ";\n");
		}

		return StringUtil.replace(content, "\n\n\n", "\n\n");
	}

	private String _updateClassDeclaration(
		String content, JavaClass javaClass) {

		Matcher classDeclarationMatcher = _classDeclarationPattern.matcher(
			content);

		if (!classDeclarationMatcher.find() ||
			!Objects.equals(
				classDeclarationMatcher.group(1), javaClass.getName())) {

			return content;
		}

		List<String> implementedClassNames = ListUtil.filter(
			javaClass.getImplementedClassNames(),
			implementedClassName -> !_portletProviderActions.containsKey(
				implementedClassName));

		List<String> extendedClassNames = javaClass.getExtendedClassNames();

		if (!extendedClassNames.contains("BasePortletProvider")) {
			implementedClassNames.add("PortletProvider");

			Collections.sort(implementedClassNames);
		}

		StringBundler sb = new StringBundler(7);

		sb.append("class ");
		sb.append(classDeclarationMatcher.group(1));

		if (!extendedClassNames.isEmpty()) {
			sb.append(" extends ");
			sb.append(classDeclarationMatcher.group(2));
		}

		if (!implementedClassNames.isEmpty()) {
			sb.append(" implements ");
			sb.append(StringUtil.merge(implementedClassNames, ", "));
		}

		sb.append(" {");

		return StringBundler.concat(
			content.substring(0, classDeclarationMatcher.start()), sb,
			content.substring(classDeclarationMatcher.end()));
	}

	private String _updateComponentService(
		String content, JavaClass javaClass) {

		if (!javaClass.hasAnnotation("Component")) {
			return content;
		}

		int x = content.lastIndexOf(
			"@Component", content.indexOf("class " + javaClass.getName()));

		if (x == -1) {
			return content;
		}

		int y = x + "@Component".length();

		if (content.charAt(y) != '(') {
			return StringBundler.concat(
				content.substring(0, x),
				"@Component(service = PortletProvider.class)",
				content.substring(y));
		}

		String component = null;

		while (true) {
			y = content.indexOf(")", y + 1);

			if (y == -1) {
				return content;
			}

			component = content.substring(x, y + 1);

			if (getLevel(component) == 0) {
				break;
			}
		}

		String newComponent = _updateServiceClassNames(component);

		return StringBundler.concat(
			content.substring(0, x), newComponent, content.substring(y + 1));
	}

	private String _updateServiceClassNames(String component) {
		Matcher serviceArrayMatcher = _serviceArrayPattern.matcher(component);

		if (serviceArrayMatcher.find()) {
			Set<String> serviceClassNames = new TreeSet<>();

			for (String serviceClassName :
					StringUtil.split(serviceArrayMatcher.group(1))) {

				serviceClassName = StringUtil.trim(serviceClassName);

				if (_portletProviderActions.containsKey(
						StringUtil.removeSubstring(
							serviceClassName, ".class"))) {

					serviceClassName = "PortletProvider.class";
				}

				serviceClassNames.add(serviceClassName);
			}

			String serviceClassNamesString = StringUtil.merge(
				serviceClassNames, ", ");

			if (serviceClassNames.size() > 1) {
				serviceClassNamesString = "{" + serviceClassNamesString + "}";
			}

			return StringUtil.replaceFirst(
				component, serviceArrayMatcher.group(),
				"service = " + serviceClassNamesString);
		}

		Matcher serviceSingleMatcher = _serviceSinglePattern.matcher(component);

		if (serviceSingleMatcher.find()) {
			if (_portletProviderActions.containsKey(
					serviceSingleMatcher.group(1))) {

				return StringUtil.replaceFirst(
					component, serviceSingleMatcher.group(),
					"service = PortletProvider.class");
			}

			return component;
		}

		String attributes = StringUtil.trim(
			component.substring(
				"@Component(".length(), component.length() - 1));

		if (attributes.isEmpty()) {
			return "@Component(service = PortletProvider.class)";
		}

		return StringBundler.concat(
			"@Component(\n\t", attributes,
			",\n\tservice = PortletProvider.class\n)");
	}

	private static final Pattern _classDeclarationPattern = Pattern.compile(
		"class\\s+(\\w+)(?:\\s+extends\\s+([\\w.]+(?:<[^{]*?>)?))?\\s+" +
			"implements\\s+[^{]+\\{");
	private static final Map<String, String> _portletProviderActions =
		TreeMapBuilder.put(
			"AddPortletProvider", "Action.ADD"
		).put(
			"BrowsePortletProvider", "Action.BROWSE"
		).put(
			"EditPortletProvider", "Action.EDIT"
		).put(
			"ManagePortletProvider", "Action.MANAGE"
		).put(
			"PreviewPortletProvider", "Action.PREVIEW"
		).put(
			"ViewPortletProvider", "Action.VIEW"
		).build();
	private static final Pattern _serviceArrayPattern = Pattern.compile(
		"service\\s*=\\s*\\{([^}]*)}");
	private static final Pattern _serviceSinglePattern = Pattern.compile(
		"service\\s*=\\s*(\\w+)\\.class");

}