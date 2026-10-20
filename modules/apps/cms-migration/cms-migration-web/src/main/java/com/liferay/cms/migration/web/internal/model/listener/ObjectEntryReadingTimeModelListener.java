/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cms.migration.web.internal.model.listener;

import com.liferay.cms.migration.web.internal.constants.CMSMigrationConstants;
import com.liferay.object.model.ObjectDefinition;
import com.liferay.object.model.ObjectEntry;
import com.liferay.object.service.ObjectDefinitionLocalService;
import com.liferay.portal.kernel.exception.ModelListenerException;
import com.liferay.portal.kernel.model.BaseModelListener;
import com.liferay.portal.kernel.model.ModelListener;
import com.liferay.portal.kernel.util.ContentTypes;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.reading.time.calculator.ReadingTimeCalculator;
import com.liferay.reading.time.service.ReadingTimeEntryLocalService;

import java.io.Serializable;

import java.time.Duration;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Keeps the reading time of migrated blog entries in sync with their content,
 * as the Blogs reading time model listener does for blog entries.
 *
 * @author Micaelle Silva
 */
@Component(service = ModelListener.class)
public class ObjectEntryReadingTimeModelListener
	extends BaseModelListener<ObjectEntry> {

	@Override
	public void onAfterRemove(ObjectEntry objectEntry)
		throws ModelListenerException {

		if (!_isMigratedBlogsEntry(objectEntry)) {
			return;
		}

		_readingTimeEntryLocalService.deleteReadingTimeEntry(
			objectEntry.getGroupId(), _portal.getClassNameId(ObjectEntry.class),
			objectEntry.getObjectEntryId());
	}

	@Override
	public void onAfterUpdate(
			ObjectEntry originalObjectEntry, ObjectEntry objectEntry)
		throws ModelListenerException {

		if (!_isMigratedBlogsEntry(objectEntry)) {
			return;
		}

		Locale locale = LocaleUtil.getSiteDefault();

		Duration readingTimeDuration = _readingTimeCalculator.calculate(
			_getContent(LocaleUtil.toLanguageId(locale), objectEntry),
			ContentTypes.TEXT_HTML, locale);

		if (readingTimeDuration == null) {
			return;
		}

		_readingTimeEntryLocalService.updateReadingTimeEntry(
			objectEntry.getGroupId(), _portal.getClassNameId(ObjectEntry.class),
			objectEntry.getObjectEntryId(), readingTimeDuration);
	}

	private String _getContent(String languageId, ObjectEntry objectEntry) {
		Map<String, Serializable> values = objectEntry.getValues();

		Object value = values.get(
			CMSMigrationConstants.OBJECT_FIELD_NAME_CONTENT + "_i18n");

		if (value instanceof Map) {
			Map<String, Object> localizedValues = (Map<String, Object>)value;

			Object localizedValue = localizedValues.get(languageId);

			if (Validator.isNotNull(localizedValue)) {
				return GetterUtil.getString(localizedValue);
			}
		}

		return GetterUtil.getString(
			values.get(CMSMigrationConstants.OBJECT_FIELD_NAME_CONTENT));
	}

	private boolean _isMigratedBlogsEntry(ObjectEntry objectEntry) {
		ObjectDefinition objectDefinition =
			_objectDefinitionLocalService.fetchObjectDefinition(
				objectEntry.getObjectDefinitionId());

		if ((objectDefinition != null) &&
			Objects.equals(
				objectDefinition.getExternalReferenceCode(),
				CMSMigrationConstants.
					OBJECT_DEFINITION_EXTERNAL_REFERENCE_CODE_BLOGS_ENTRY)) {

			return true;
		}

		return false;
	}

	@Reference
	private ObjectDefinitionLocalService _objectDefinitionLocalService;

	@Reference
	private Portal _portal;

	@Reference
	private ReadingTimeCalculator _readingTimeCalculator;

	@Reference
	private ReadingTimeEntryLocalService _readingTimeEntryLocalService;

}