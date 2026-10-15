/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cms.migration.web.internal.reference;

import com.liferay.dynamic.data.mapping.model.DDMStructure;
import com.liferay.object.model.ObjectDefinition;

/**
 * Describes the item type that implicit references resolve against: the
 * displayed item of a display page template, or the items of a collection.
 *
 * @author Lino Alves
 */
public class ReferenceScope {

	public ReferenceScope(
		DDMStructure ddmStructure, ObjectDefinition objectDefinition,
		String sourceClassName) {

		_ddmStructure = ddmStructure;
		_objectDefinition = objectDefinition;
		_sourceClassName = sourceClassName;
	}

	public DDMStructure getDDMStructure() {
		return _ddmStructure;
	}

	public ObjectDefinition getObjectDefinition() {
		return _objectDefinition;
	}

	public String getSourceClassName() {
		return _sourceClassName;
	}

	private final DDMStructure _ddmStructure;
	private final ObjectDefinition _objectDefinition;
	private final String _sourceClassName;

}