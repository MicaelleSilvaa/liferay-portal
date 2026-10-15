/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cms.migration.web.internal.content;

import com.liferay.cms.migration.web.internal.constants.CMSMigrationConstants;
import com.liferay.cms.migration.web.internal.migration.CMSMigrationContext;
import com.liferay.cms.migration.web.internal.structure.DDMFormValuesConverter;
import com.liferay.cms.migration.web.internal.structure.ObjectDefinitionMigrator;
import com.liferay.journal.constants.JournalArticleConstants;
import com.liferay.journal.constants.JournalFolderConstants;
import com.liferay.journal.model.JournalArticle;
import com.liferay.journal.model.JournalArticleResource;
import com.liferay.journal.model.JournalFolder;
import com.liferay.journal.service.JournalArticleLocalService;
import com.liferay.journal.service.JournalArticleResourceLocalService;
import com.liferay.object.model.ObjectDefinition;
import com.liferay.object.model.ObjectEntry;
import com.liferay.petra.string.StringBundler;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.module.service.Snapshot;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.workflow.WorkflowConstants;

import java.io.Serializable;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Migrates the latest version of every web content article of a site. An
 * article with an approved version migrates that version; an article that was
 * never approved migrates as a draft.
 *
 * @author Lino Alves
 */
public class JournalArticleMigrator {

	public List<JournalArticleResource> getJournalArticleResources(
		CMSMigrationContext cmsMigrationContext) {

		return _journalArticleResourceLocalService.getArticleResources(
			cmsMigrationContext.getSourceGroupId());
	}

	public ObjectEntry migrate(
			CMSMigrationContext cmsMigrationContext, long resourcePrimKey)
		throws PortalException {

		ObjectEntry objectEntry = cmsMigrationContext.getObjectEntry(
			JournalArticle.class.getName(), resourcePrimKey);

		if (objectEntry != null) {
			return objectEntry;
		}

		JournalArticle journalArticle =
			_journalArticleLocalService.fetchLatestArticle(
				resourcePrimKey, WorkflowConstants.STATUS_APPROVED);

		boolean approved = true;

		if (journalArticle == null) {
			approved = false;

			journalArticle = _journalArticleLocalService.fetchLatestArticle(
				resourcePrimKey, WorkflowConstants.STATUS_ANY);
		}

		if ((journalArticle == null) || journalArticle.isInTrash() ||
			(journalArticle.getClassNameId() !=
				JournalArticleConstants.CLASS_NAME_ID_DEFAULT)) {

			return null;
		}

		ObjectDefinition objectDefinition =
			_objectDefinitionMigrator.getOrAddDDMStructureObjectDefinition(
				cmsMigrationContext, journalArticle.getDDMStructure());

		String externalReferenceCode =
			CMSMigrationConstants.
				EXTERNAL_REFERENCE_CODE_PREFIX_JOURNAL_ARTICLE +
					resourcePrimKey;

		Map<String, Serializable> values = _ddmFormValuesConverter.getValues(
			cmsMigrationContext, journalArticle.getDDMFormValues());

		values.put(
			CMSMigrationConstants.OBJECT_FIELD_NAME_DESCRIPTION + "_i18n",
			LocalizedValuesUtil.toValues(
				journalArticle.getDescriptionMap(), value -> value));
		values.put(
			CMSMigrationConstants.OBJECT_FIELD_NAME_TITLE + "_i18n",
			LocalizedValuesUtil.toValues(
				journalArticle.getTitleMap(),
				value -> _truncateTitle(cmsMigrationContext, value)));

		_putSmallImageValues(
			cmsMigrationContext, externalReferenceCode, journalArticle,
			objectDefinition, values);

		Date expirationDate = journalArticle.getExpirationDate();

		if ((expirationDate != null) && expirationDate.after(new Date())) {
			values.put("expirationDate", expirationDate);
		}

		objectEntry = _objectEntryWriter.write(
			cmsMigrationContext, JournalArticle.class.getName(),
			resourcePrimKey, objectDefinition,
			_getObjectEntryFolderId(cmsMigrationContext, journalArticle),
			externalReferenceCode, journalArticle.getUserId(),
			journalArticle.getDefaultLanguageId(), values,
			LocalizedValuesUtil.toLanguageIdMap(
				journalArticle.getFriendlyURLMap()),
			journalArticle.getCreateDate(), journalArticle.getModifiedDate(),
			approved);

		cmsMigrationContext.getCMSMigrationReport(
		).increment(
			CMSMigrationConstants.PHASE_JOURNAL_ARTICLES
		);

		return objectEntry;
	}

	public void migrateAll(CMSMigrationContext cmsMigrationContext) {
		for (JournalArticleResource journalArticleResource :
				getJournalArticleResources(cmsMigrationContext)) {

			cmsMigrationContext.sendStatus(
				journalArticleResource.getArticleId());

			try {
				migrate(
					cmsMigrationContext,
					journalArticleResource.getResourcePrimKey());
			}
			catch (Exception exception) {
				cmsMigrationContext.getCMSMigrationReport(
				).addError(
					StringBundler.concat(
						"Unable to migrate the web content ",
						journalArticleResource.getArticleId(), ": ",
						exception.getMessage())
				);
			}
		}
	}

	private long _getObjectEntryFolderId(
		CMSMigrationContext cmsMigrationContext,
		JournalArticle journalArticle) {

		long objectEntryFolderId = cmsMigrationContext.getObjectEntryFolderId(
			JournalFolder.class.getName(), journalArticle.getFolderId());

		if (objectEntryFolderId == 0) {
			objectEntryFolderId = cmsMigrationContext.getObjectEntryFolderId(
				JournalFolder.class.getName(),
				JournalFolderConstants.DEFAULT_PARENT_FOLDER_ID);
		}

		return objectEntryFolderId;
	}

	private void _putSmallImageValues(
		CMSMigrationContext cmsMigrationContext, String externalReferenceCode,
		JournalArticle journalArticle, ObjectDefinition objectDefinition,
		Map<String, Serializable> values) {

		int smallImageSource = journalArticle.getSmallImageSource();

		long fileEntryId = 0;

		if (smallImageSource ==
				JournalArticleConstants.
					SMALL_IMAGE_SOURCE_DOCUMENTS_AND_MEDIA) {

			fileEntryId = _attachmentCopier.copy(
				cmsMigrationContext, objectDefinition,
				CMSMigrationConstants.OBJECT_FIELD_NAME_SMALL_IMAGE,
				externalReferenceCode + "_SMALL_IMAGE",
				journalArticle.getSmallImageId());
		}
		else if (smallImageSource ==
					JournalArticleConstants.SMALL_IMAGE_SOURCE_URL) {

			if (Validator.isNotNull(journalArticle.getSmallImageURL())) {
				values.put(
					CMSMigrationConstants.OBJECT_FIELD_NAME_SMALL_IMAGE_URL,
					journalArticle.getSmallImageURL());
			}
		}
		else if (smallImageSource ==
					JournalArticleConstants.SMALL_IMAGE_SOURCE_USER_COMPUTER) {

			fileEntryId = _attachmentCopier.copyImage(
				cmsMigrationContext, objectDefinition,
				CMSMigrationConstants.OBJECT_FIELD_NAME_SMALL_IMAGE,
				externalReferenceCode + "_SMALL_IMAGE",
				journalArticle.getSmallImageId());
		}

		if (fileEntryId > 0) {
			values.put(
				CMSMigrationConstants.OBJECT_FIELD_NAME_SMALL_IMAGE,
				fileEntryId);
		}
	}

	private String _truncateTitle(
		CMSMigrationContext cmsMigrationContext, String title) {

		if (title.length() > _TITLE_MAX_LENGTH) {
			cmsMigrationContext.getCMSMigrationReport(
			).addWarning(
				"Truncated the title of the web content " + title
			);
		}

		return LocalizedValuesUtil.truncate(title, _TITLE_MAX_LENGTH);
	}

	private static final int _TITLE_MAX_LENGTH = 280;

	private static final Snapshot<JournalArticleLocalService>
		_journalArticleLocalServiceSnapshot = new Snapshot<>(
			JournalArticleMigrator.class, JournalArticleLocalService.class);
	private static final Snapshot<JournalArticleResourceLocalService>
		_journalArticleResourceLocalServiceSnapshot = new Snapshot<>(
			JournalArticleMigrator.class,
			JournalArticleResourceLocalService.class);

	private final AttachmentCopier _attachmentCopier = new AttachmentCopier();
	private final DDMFormValuesConverter _ddmFormValuesConverter =
		new DDMFormValuesConverter();
	private final JournalArticleLocalService _journalArticleLocalService =
		_journalArticleLocalServiceSnapshot.get();
	private final JournalArticleResourceLocalService
		_journalArticleResourceLocalService =
			_journalArticleResourceLocalServiceSnapshot.get();
	private final ObjectDefinitionMigrator _objectDefinitionMigrator =
		new ObjectDefinitionMigrator();
	private final ObjectEntryWriter _objectEntryWriter =
		new ObjectEntryWriter();

}