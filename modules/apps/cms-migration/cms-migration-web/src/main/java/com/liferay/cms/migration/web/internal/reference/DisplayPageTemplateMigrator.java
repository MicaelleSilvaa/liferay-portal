/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cms.migration.web.internal.reference;

import com.liferay.asset.display.page.constants.AssetDisplayPageConstants;
import com.liferay.asset.display.page.model.AssetDisplayPageEntry;
import com.liferay.asset.display.page.service.AssetDisplayPageEntryLocalService;
import com.liferay.blogs.model.BlogsEntry;
import com.liferay.cms.migration.web.internal.constants.CMSMigrationConstants;
import com.liferay.cms.migration.web.internal.migration.CMSMigrationContext;
import com.liferay.cms.migration.web.internal.structure.ObjectDefinitionMigrator;
import com.liferay.document.library.kernel.model.DLFileEntry;
import com.liferay.document.library.kernel.model.DLFileEntryType;
import com.liferay.document.library.kernel.service.DLFileEntryTypeLocalService;
import com.liferay.dynamic.data.mapping.model.DDMStructure;
import com.liferay.dynamic.data.mapping.service.DDMStructureLocalService;
import com.liferay.layout.page.template.constants.LayoutPageTemplateEntryTypeConstants;
import com.liferay.layout.page.template.model.LayoutPageTemplateEntry;
import com.liferay.layout.page.template.service.LayoutPageTemplateEntryLocalService;
import com.liferay.object.model.ObjectDefinition;
import com.liferay.object.model.ObjectEntry;
import com.liferay.petra.string.StringBundler;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.model.Layout;
import com.liferay.portal.kernel.module.service.Snapshot;
import com.liferay.portal.kernel.repository.model.FileEntry;
import com.liferay.portal.kernel.service.LayoutLocalService;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.workflow.WorkflowConstants;

import java.util.Map;

/**
 * Copies every Web Content, Blogs, and Documents and Media display page
 * template of a site into a display page template of the object definition
 * that replaces its type, keeps the default assignments, and remaps the
 * displayed item fields.
 *
 * @author Lino Alves
 */
public class DisplayPageTemplateMigrator {

	public void migrate(CMSMigrationContext cmsMigrationContext) {
		for (LayoutPageTemplateEntry layoutPageTemplateEntry :
				_layoutPageTemplateEntryLocalService.
					getLayoutPageTemplateEntries(
						cmsMigrationContext.getSourceGroupId())) {

			if (layoutPageTemplateEntry.getType() !=
					LayoutPageTemplateEntryTypeConstants.DISPLAY_PAGE) {

				continue;
			}

			String sourceClassName =
				_infoItemReferenceMapper.getSourceClassName(
					layoutPageTemplateEntry.getClassNameId());

			if (sourceClassName == null) {
				continue;
			}

			cmsMigrationContext.sendStatus(layoutPageTemplateEntry.getName());

			try {
				_migrate(
					cmsMigrationContext, layoutPageTemplateEntry,
					sourceClassName);
			}
			catch (Exception exception) {
				cmsMigrationContext.getCMSMigrationReport(
				).addError(
					StringBundler.concat(
						"Unable to migrate the display page template ",
						layoutPageTemplateEntry.getName(), ": ",
						exception.getMessage())
				);
			}
		}

		_migrateAssetDisplayPageEntries(cmsMigrationContext);
	}

	private ReferenceScope _getReferenceScope(
			CMSMigrationContext cmsMigrationContext,
			LayoutPageTemplateEntry layoutPageTemplateEntry,
			String sourceClassName)
		throws Exception {

		if (sourceClassName.equals(BlogsEntry.class.getName())) {
			if (!cmsMigrationContext.isMigrateBlogsEntries()) {
				return null;
			}

			return new ReferenceScope(
				null,
				_objectDefinitionMigrator.getOrAddBlogsEntryObjectDefinition(
					cmsMigrationContext),
				sourceClassName);
		}
		else if (sourceClassName.equals(DLFileEntry.class.getName())) {
			if (!cmsMigrationContext.isMigrateFileEntries()) {
				return null;
			}

			DLFileEntryType dlFileEntryType =
				_dlFileEntryTypeLocalService.fetchDLFileEntryType(
					layoutPageTemplateEntry.getClassTypeId());

			return new ReferenceScope(
				null,
				_objectDefinitionMigrator.
					getOrAddDLFileEntryTypeObjectDefinition(
						cmsMigrationContext, dlFileEntryType),
				sourceClassName);
		}

		if (!cmsMigrationContext.isMigrateJournalArticles()) {
			return null;
		}

		DDMStructure ddmStructure = _ddmStructureLocalService.fetchDDMStructure(
			layoutPageTemplateEntry.getClassTypeId());

		if (ddmStructure == null) {
			return null;
		}

		return new ReferenceScope(
			ddmStructure,
			_objectDefinitionMigrator.getOrAddDDMStructureObjectDefinition(
				cmsMigrationContext, ddmStructure),
			sourceClassName);
	}

	private void _migrate(
			CMSMigrationContext cmsMigrationContext,
			LayoutPageTemplateEntry layoutPageTemplateEntry,
			String sourceClassName)
		throws Exception {

		ReferenceScope referenceScope = _getReferenceScope(
			cmsMigrationContext, layoutPageTemplateEntry, sourceClassName);

		if (referenceScope == null) {
			return;
		}

		ObjectDefinition objectDefinition =
			referenceScope.getObjectDefinition();

		String externalReferenceCode =
			CMSMigrationConstants.
				EXTERNAL_REFERENCE_CODE_PREFIX_LAYOUT_PAGE_TEMPLATE_ENTRY +
					layoutPageTemplateEntry.getLayoutPageTemplateEntryId();

		LayoutPageTemplateEntry newLayoutPageTemplateEntry =
			_layoutPageTemplateEntryLocalService.
				fetchLayoutPageTemplateEntryByExternalReferenceCode(
					externalReferenceCode,
					cmsMigrationContext.getSourceGroupId());

		if (newLayoutPageTemplateEntry == null) {
			Layout layout = _layoutLocalService.getLayout(
				layoutPageTemplateEntry.getPlid());

			newLayoutPageTemplateEntry =
				_layoutPageTemplateEntryLocalService.addLayoutPageTemplateEntry(
					externalReferenceCode, cmsMigrationContext.getUserId(),
					cmsMigrationContext.getSourceGroupId(),
					layoutPageTemplateEntry.getLayoutPageTemplateCollectionId(),
					null,
					_portal.getClassNameId(objectDefinition.getClassName()),
					null, layoutPageTemplateEntry.getName() + " (CMS)",
					LayoutPageTemplateEntryTypeConstants.DISPLAY_PAGE,
					layout.getMasterLayoutPlid(),
					WorkflowConstants.STATUS_APPROVED,
					cmsMigrationContext.getServiceContext());

			Layout newLayout = _layoutLocalService.getLayout(
				newLayoutPageTemplateEntry.getPlid());

			_layoutLocalService.copyLayoutContent(layout, newLayout);

			Layout draftLayout = newLayout.fetchDraftLayout();

			if (draftLayout != null) {
				_layoutLocalService.copyLayoutContent(layout, draftLayout);
			}

			cmsMigrationContext.getCMSMigrationReport(
			).increment(
				CMSMigrationConstants.PHASE_DISPLAY_PAGE_TEMPLATES
			);
		}

		if (layoutPageTemplateEntry.isDefaultTemplate() &&
			!newLayoutPageTemplateEntry.isDefaultTemplate()) {

			newLayoutPageTemplateEntry =
				_layoutPageTemplateEntryLocalService.
					updateLayoutPageTemplateEntry(
						newLayoutPageTemplateEntry.
							getLayoutPageTemplateEntryId(),
						true);
		}

		Layout newLayout = _layoutLocalService.getLayout(
			newLayoutPageTemplateEntry.getPlid());

		_layoutReferenceRewriter.rewrite(
			cmsMigrationContext, newLayout, referenceScope);

		Layout draftLayout = newLayout.fetchDraftLayout();

		if (draftLayout != null) {
			_layoutReferenceRewriter.rewrite(
				cmsMigrationContext, draftLayout, referenceScope);
		}

		cmsMigrationContext.addLayoutPageTemplateEntryId(
			layoutPageTemplateEntry.getLayoutPageTemplateEntryId(),
			newLayoutPageTemplateEntry.getLayoutPageTemplateEntryId());
	}

	private void _migrateAssetDisplayPageEntries(
		CMSMigrationContext cmsMigrationContext) {

		for (Map.Entry<String, ObjectEntry> entry :
				cmsMigrationContext.getObjectEntriesMap(
				).entrySet()) {

			String[] keyParts = StringUtil.split(
				entry.getKey(), StringPool.POUND);

			String className = keyParts[0];

			if (className.equals(DLFileEntry.class.getName())) {
				className = FileEntry.class.getName();
			}

			AssetDisplayPageEntry assetDisplayPageEntry =
				_assetDisplayPageEntryLocalService.fetchAssetDisplayPageEntry(
					cmsMigrationContext.getSourceGroupId(),
					_portal.getClassNameId(className),
					GetterUtil.getLong(keyParts[1]));

			if (assetDisplayPageEntry == null) {
				continue;
			}

			long layoutPageTemplateEntryId =
				cmsMigrationContext.getLayoutPageTemplateEntryId(
					assetDisplayPageEntry.getLayoutPageTemplateEntryId());

			if ((assetDisplayPageEntry.getType() ==
					AssetDisplayPageConstants.TYPE_SPECIFIC) &&
				(layoutPageTemplateEntryId == 0)) {

				continue;
			}

			ObjectEntry objectEntry = entry.getValue();

			ObjectDefinition objectDefinition =
				_infoItemReferenceMapper.getObjectDefinition(objectEntry);

			long classNameId = _portal.getClassNameId(
				objectDefinition.getClassName());

			try {
				AssetDisplayPageEntry objectAssetDisplayPageEntry =
					_assetDisplayPageEntryLocalService.
						fetchAssetDisplayPageEntry(
							objectEntry.getGroupId(), classNameId,
							objectEntry.getObjectEntryId());

				if (objectAssetDisplayPageEntry == null) {
					_assetDisplayPageEntryLocalService.addAssetDisplayPageEntry(
						cmsMigrationContext.getUserId(),
						objectEntry.getGroupId(), classNameId,
						objectEntry.getObjectEntryId(),
						layoutPageTemplateEntryId,
						assetDisplayPageEntry.getType(),
						cmsMigrationContext.getServiceContext());
				}
				else {
					_assetDisplayPageEntryLocalService.
						updateAssetDisplayPageEntry(
							objectAssetDisplayPageEntry.
								getAssetDisplayPageEntryId(),
							layoutPageTemplateEntryId,
							assetDisplayPageEntry.getType());
				}
			}
			catch (PortalException portalException) {
				cmsMigrationContext.getCMSMigrationReport(
				).addWarning(
					StringBundler.concat(
						"Unable to assign the display page of ",
						objectEntry.getExternalReferenceCode(), ": ",
						portalException.getMessage())
				);
			}
		}
	}

	private static final Snapshot<AssetDisplayPageEntryLocalService>
		_assetDisplayPageEntryLocalServiceSnapshot = new Snapshot<>(
			DisplayPageTemplateMigrator.class,
			AssetDisplayPageEntryLocalService.class);
	private static final Snapshot<DDMStructureLocalService>
		_ddmStructureLocalServiceSnapshot = new Snapshot<>(
			DisplayPageTemplateMigrator.class, DDMStructureLocalService.class);
	private static final Snapshot<DLFileEntryTypeLocalService>
		_dlFileEntryTypeLocalServiceSnapshot = new Snapshot<>(
			DisplayPageTemplateMigrator.class,
			DLFileEntryTypeLocalService.class);
	private static final Snapshot<LayoutLocalService>
		_layoutLocalServiceSnapshot = new Snapshot<>(
			DisplayPageTemplateMigrator.class, LayoutLocalService.class);
	private static final Snapshot<LayoutPageTemplateEntryLocalService>
		_layoutPageTemplateEntryLocalServiceSnapshot = new Snapshot<>(
			DisplayPageTemplateMigrator.class,
			LayoutPageTemplateEntryLocalService.class);
	private static final Snapshot<Portal> _portalSnapshot = new Snapshot<>(
		DisplayPageTemplateMigrator.class, Portal.class);

	private final AssetDisplayPageEntryLocalService
		_assetDisplayPageEntryLocalService =
			_assetDisplayPageEntryLocalServiceSnapshot.get();
	private final DDMStructureLocalService _ddmStructureLocalService =
		_ddmStructureLocalServiceSnapshot.get();
	private final DLFileEntryTypeLocalService _dlFileEntryTypeLocalService =
		_dlFileEntryTypeLocalServiceSnapshot.get();
	private final InfoItemReferenceMapper _infoItemReferenceMapper =
		new InfoItemReferenceMapper();
	private final LayoutLocalService _layoutLocalService =
		_layoutLocalServiceSnapshot.get();
	private final LayoutPageTemplateEntryLocalService
		_layoutPageTemplateEntryLocalService =
			_layoutPageTemplateEntryLocalServiceSnapshot.get();
	private final LayoutReferenceRewriter _layoutReferenceRewriter =
		new LayoutReferenceRewriter();
	private final ObjectDefinitionMigrator _objectDefinitionMigrator =
		new ObjectDefinitionMigrator();
	private final Portal _portal = _portalSnapshot.get();

}