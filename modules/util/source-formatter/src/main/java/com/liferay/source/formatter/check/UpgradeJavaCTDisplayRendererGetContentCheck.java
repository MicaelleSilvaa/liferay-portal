/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.source.formatter.check;

import com.liferay.petra.string.CharPool;
import com.liferay.petra.string.StringBundler;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.source.formatter.parser.JavaClass;
import com.liferay.source.formatter.parser.JavaClassParser;
import com.liferay.source.formatter.parser.JavaMethod;
import com.liferay.source.formatter.parser.JavaParameter;
import com.liferay.source.formatter.parser.JavaSignature;
import com.liferay.source.formatter.parser.JavaTerm;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * @author Micaelle Silva
 */
public class UpgradeJavaCTDisplayRendererGetContentCheck
	extends BaseUpgradeCheck {

	@Override
	protected String format(
			String fileName, String absolutePath, String content)
		throws Exception {

		JavaClass javaClass = JavaClassParser.parseJavaClass(fileName, content);

		if (!_extendsBaseCTDisplayRenderer(javaClass)) {
			return content;
		}

		for (JavaTerm childJavaTerm : javaClass.getChildJavaTerms()) {
			if (!childJavaTerm.isJavaMethod()) {
				continue;
			}

			JavaMethod javaMethod = (JavaMethod)childJavaTerm;

			String javaMethodContent = javaMethod.getContent();

			String newJavaMethodContent = _formatGetContentMethod(javaMethod);

			if (newJavaMethodContent == null) {
				continue;
			}

			content = StringUtil.replace(
				content, javaMethodContent, newJavaMethodContent);
		}

		return content;
	}

	@Override
	protected String[] getNewImports() {
		return new String[] {
			"com.liferay.change.tracking.spi.display.context.DisplayContext"
		};
	}

	private boolean _extendsBaseCTDisplayRenderer(JavaClass javaClass) {
		for (String extendedClassName : javaClass.getExtendedClassNames()) {
			if (extendedClassName.equals("BaseCTDisplayRenderer") ||
				extendedClassName.startsWith("BaseCTDisplayRenderer<")) {

				return true;
			}
		}

		return false;
	}

	private String _formatGetContentMethod(JavaMethod javaMethod) {
		if (!Objects.equals(javaMethod.getName(), "getContent")) {
			return null;
		}

		JavaSignature javaSignature = javaMethod.getSignature();

		List<JavaParameter> parameters = javaSignature.getParameters();

		if (!Objects.equals(javaSignature.getReturnType(), "String") ||
			(parameters.size() != 4) ||
			!Objects.equals(
				parameters.get(
					0
				).getParameterType(),
				"HttpServletRequest") ||
			!Objects.equals(
				parameters.get(
					1
				).getParameterType(),
				"HttpServletResponse") ||
			!Objects.equals(
				parameters.get(
					2
				).getParameterType(),
				"Locale")) {

			return null;
		}

		String javaMethodContent = javaMethod.getContent();

		int openCurlyBraceIndex = javaMethodContent.indexOf(
			CharPool.OPEN_CURLY_BRACE);

		if (openCurlyBraceIndex == -1) {
			return null;
		}

		String methodBody = javaMethodContent.substring(
			openCurlyBraceIndex + 1);

		JavaParameter modelParameter = parameters.get(3);

		if (_referencesRemovedParameters(
				methodBody, parameters.subList(0, 3))) {

			return null;
		}

		String modelType = modelParameter.getParameterType();
		String modelVariableName = modelParameter.getParameterName();

		return StringBundler.concat(
			"\t@Override\n\tpublic String renderPreview(DisplayContext<",
			modelType, "> displayContext)\n\t\tthrows Exception {\n\n\t\t",
			modelType, " ", modelVariableName, " = displayContext.getModel();",
			methodBody);
	}

	private boolean _referencesRemovedParameters(
		String methodBody, List<JavaParameter> removedParameters) {

		for (JavaParameter removedParameter : removedParameters) {
			Pattern pattern = Pattern.compile(
				"\\b" + removedParameter.getParameterName() + "\\b");

			if (pattern.matcher(
					methodBody
				).find()) {

				return true;
			}
		}

		return false;
	}

}