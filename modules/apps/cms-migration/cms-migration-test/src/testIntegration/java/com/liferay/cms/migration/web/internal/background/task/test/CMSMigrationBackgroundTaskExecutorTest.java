/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cms.migration.web.internal.background.task.test;

import com.liferay.arquillian.extension.junit.bridge.junit.Arquillian;
import com.liferay.blogs.model.BlogsEntry;
import com.liferay.blogs.service.BlogsEntryLocalService;
import com.liferay.depot.constants.DepotConstants;
import com.liferay.depot.model.DepotEntry;
import com.liferay.depot.service.DepotEntryLocalService;
import com.liferay.document.library.kernel.model.DLFolderConstants;
import com.liferay.document.library.kernel.service.DLAppLocalService;
import com.liferay.fragment.model.FragmentEntryLink;
import com.liferay.fragment.service.FragmentEntryLinkLocalService;
import com.liferay.journal.constants.JournalContentPortletKeys;
import com.liferay.journal.constants.JournalFolderConstants;
import com.liferay.journal.model.JournalArticle;
import com.liferay.journal.test.util.JournalTestUtil;
import com.liferay.layout.test.util.ContentLayoutTestUtil;
import com.liferay.layout.test.util.LayoutTestUtil;
import com.liferay.object.model.ObjectDefinition;
import com.liferay.object.model.ObjectEntry;
import com.liferay.object.service.ObjectDefinitionLocalService;
import com.liferay.object.service.ObjectEntryLocalService;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.backgroundtask.BackgroundTask;
import com.liferay.portal.kernel.backgroundtask.BackgroundTaskExecutor;
import com.liferay.portal.kernel.backgroundtask.BackgroundTaskResult;
import com.liferay.portal.kernel.backgroundtask.constants.BackgroundTaskConstants;
import com.liferay.portal.kernel.json.JSONFactoryUtil;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.json.JSONUtil;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.model.Layout;
import com.liferay.portal.kernel.model.LayoutTypePortlet;
import com.liferay.portal.kernel.portlet.PortletIdCodec;
import com.liferay.portal.kernel.repository.model.FileEntry;
import com.liferay.portal.kernel.service.LayoutLocalService;
import com.liferay.portal.kernel.service.PortletPreferencesLocalService;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.test.rule.AggregateTestRule;
import com.liferay.portal.kernel.test.rule.DeleteAfterTestRun;
import com.liferay.portal.kernel.test.util.GroupTestUtil;
import com.liferay.portal.kernel.test.util.RandomTestUtil;
import com.liferay.portal.kernel.test.util.ServiceContextTestUtil;
import com.liferay.portal.kernel.test.util.TestPropsValues;
import com.liferay.portal.kernel.util.ContentTypes;
import com.liferay.portal.kernel.util.HashMapBuilder;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.portal.kernel.util.PortletKeys;
import com.liferay.portal.kernel.util.ProxyUtil;
import com.liferay.portal.test.rule.Inject;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;
import com.liferay.portal.test.rule.PermissionCheckerMethodTestRule;
import com.liferay.segments.service.SegmentsExperienceLocalService;

import jakarta.portlet.PortletPreferences;

import java.io.Serializable;

import java.util.Map;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Lino Alves
 */
@RunWith(Arquillian.class)
public class CMSMigrationBackgroundTaskExecutorTest {

	@ClassRule
	@Rule
	public static final AggregateTestRule aggregateTestRule =
		new AggregateTestRule(
			new LiferayIntegrationTestRule(),
			PermissionCheckerMethodTestRule.INSTANCE);

	@Before
	public void setUp() throws Exception {
		_group = GroupTestUtil.addGroup();

		_depotEntry = _depotEntryLocalService.addDepotEntry(
			HashMapBuilder.put(
				LocaleUtil.getDefault(), RandomTestUtil.randomString()
			).build(),
			HashMapBuilder.put(
				LocaleUtil.getDefault(), RandomTestUtil.randomString()
			).build(),
			DepotConstants.TYPE_SPACE,
			ServiceContextTestUtil.getServiceContext(_group.getGroupId()));
	}

	@After
	public void tearDown() throws Exception {
		for (String externalReferenceCode :
				new String[] {
					"CMS_MIGRATION_BLOGS_ENTRY",
					"CMS_MIGRATION_DL_FILE_ENTRY_TYPE_0"
				}) {

			ObjectDefinition objectDefinition =
				_objectDefinitionLocalService.
					fetchObjectDefinitionByExternalReferenceCode(
						externalReferenceCode, _group.getCompanyId());

			if (objectDefinition != null) {
				_objectDefinitionLocalService.deleteObjectDefinition(
					objectDefinition);
			}
		}

		if (_journalArticle != null) {
			ObjectDefinition objectDefinition =
				_objectDefinitionLocalService.
					fetchObjectDefinitionByExternalReferenceCode(
						"CMS_MIGRATION_DDM_STRUCTURE_" +
							_journalArticle.getDDMStructureId(),
						_group.getCompanyId());

			if (objectDefinition != null) {
				_objectDefinitionLocalService.deleteObjectDefinition(
					objectDefinition);
			}
		}

		_depotEntryLocalService.deleteDepotEntry(_depotEntry);
	}

	@Test
	public void testExecute() throws Exception {
		ServiceContext serviceContext =
			ServiceContextTestUtil.getServiceContext(_group.getGroupId());

		BlogsEntry blogsEntry = _blogsEntryLocalService.addEntry(
			TestPropsValues.getUserId(), RandomTestUtil.randomString(),
			"<p>" + RandomTestUtil.randomString() + "</p>", serviceContext);

		FileEntry fileEntry = _dlAppLocalService.addFileEntry(
			null, TestPropsValues.getUserId(), _group.getGroupId(),
			DLFolderConstants.DEFAULT_PARENT_FOLDER_ID,
			RandomTestUtil.randomString() + ".txt", ContentTypes.TEXT_PLAIN,
			RandomTestUtil.randomBytes(), null, null, null, serviceContext);

		_journalArticle = JournalTestUtil.addArticle(
			_group.getGroupId(),
			JournalFolderConstants.DEFAULT_PARENT_FOLDER_ID);

		_assertSuccessful(_execute(false));

		ObjectEntry objectEntry = _getObjectEntry(
			"CMS_MIGRATION_BLOGS_ENTRY",
			"CMS_MIGRATION_BLOGS_ENTRY_" + blogsEntry.getEntryId());

		Assert.assertEquals(
			blogsEntry.getTitle(), objectEntry.getTitleValue("en_US"));

		objectEntry = _getObjectEntry(
			"CMS_MIGRATION_DL_FILE_ENTRY_TYPE_0",
			"CMS_MIGRATION_FILE_ENTRY_" + fileEntry.getFileEntryId());

		Map<String, Serializable> values = objectEntry.getValues();

		Assert.assertNotEquals(0L, values.get("file"));

		objectEntry = _getObjectEntry(
			"CMS_MIGRATION_DDM_STRUCTURE_" +
				_journalArticle.getDDMStructureId(),
			"CMS_MIGRATION_JOURNAL_ARTICLE_" +
				_journalArticle.getResourcePrimKey());

		Assert.assertEquals(
			_journalArticle.getTitle(LocaleUtil.getSiteDefault()),
			objectEntry.getTitleValue(
				LocaleUtil.toLanguageId(LocaleUtil.getSiteDefault())));

		_assertSuccessful(_execute(false));

		Assert.assertEquals(
			objectEntry.getObjectEntryId(),
			_getObjectEntry(
				"CMS_MIGRATION_DDM_STRUCTURE_" +
					_journalArticle.getDDMStructureId(),
				"CMS_MIGRATION_JOURNAL_ARTICLE_" +
					_journalArticle.getResourcePrimKey()
			).getObjectEntryId());
	}

	@Test
	public void testExecuteWithRewritePages() throws Exception {
		_journalArticle = JournalTestUtil.addArticle(
			_group.getGroupId(),
			JournalFolderConstants.DEFAULT_PARENT_FOLDER_ID);

		Layout contentLayout = LayoutTestUtil.addTypeContentLayout(_group);

		FragmentEntryLink fragmentEntryLink =
			ContentLayoutTestUtil.addFragmentEntryLinkToLayout(
				JSONUtil.put(
					"com.liferay.fragment.entry.processor.editable." +
						"EditableFragmentEntryProcessor",
					JSONUtil.put(
						"element-text",
						JSONUtil.put(
							"classNameId",
							String.valueOf(
								_portal.getClassNameId(JournalArticle.class))
						).put(
							"classPK",
							String.valueOf(_journalArticle.getResourcePrimKey())
						).put(
							"defaultValue", RandomTestUtil.randomString()
						).put(
							"fieldId", "JournalArticle_title"
						))
				).toString(),
				contentLayout,
				_segmentsExperienceLocalService.
					fetchDefaultSegmentsExperienceId(contentLayout.getPlid()));

		Layout portletLayout = LayoutTestUtil.addTypePortletLayout(_group);

		String portletId = LayoutTestUtil.addPortletToLayout(
			portletLayout, JournalContentPortletKeys.JOURNAL_CONTENT,
			HashMapBuilder.put(
				"articleId", new String[] {_journalArticle.getArticleId()}
			).put(
				"groupId",
				new String[] {String.valueOf(_journalArticle.getGroupId())}
			).build());

		_assertSuccessful(_execute(true));

		ObjectEntry objectEntry = _getObjectEntry(
			"CMS_MIGRATION_DDM_STRUCTURE_" +
				_journalArticle.getDDMStructureId(),
			"CMS_MIGRATION_JOURNAL_ARTICLE_" +
				_journalArticle.getResourcePrimKey());

		ObjectDefinition objectDefinition =
			_objectDefinitionLocalService.getObjectDefinition(
				objectEntry.getObjectDefinitionId());

		fragmentEntryLink = _fragmentEntryLinkLocalService.getFragmentEntryLink(
			fragmentEntryLink.getFragmentEntryLinkId());

		JSONObject editableValuesJSONObject = JSONFactoryUtil.createJSONObject(
			fragmentEntryLink.getEditableValues());

		JSONObject elementTextJSONObject =
			editableValuesJSONObject.getJSONObject(
				"com.liferay.fragment.entry.processor.editable." +
					"EditableFragmentEntryProcessor"
			).getJSONObject(
				"element-text"
			);

		Assert.assertEquals(
			String.valueOf(
				_portal.getClassNameId(objectDefinition.getClassName())),
			elementTextJSONObject.getString("classNameId"));
		Assert.assertEquals(
			String.valueOf(objectEntry.getObjectEntryId()),
			elementTextJSONObject.getString("classPK"));
		Assert.assertEquals(
			"ObjectField_title", elementTextJSONObject.getString("fieldId"));

		portletLayout = _layoutLocalService.getLayout(portletLayout.getPlid());

		LayoutTypePortlet layoutTypePortlet =
			(LayoutTypePortlet)portletLayout.getLayoutType();

		String newPortletId = PortletIdCodec.encode(
			_CMS_CONTENT_DISPLAY_PORTLET_NAME,
			PortletIdCodec.decodeInstanceId(portletId));

		Assert.assertTrue(
			layoutTypePortlet.getPortletIds(
			).contains(
				newPortletId
			));

		Assert.assertFalse(
			layoutTypePortlet.getPortletIds(
			).contains(
				portletId
			));

		PortletPreferences portletPreferences =
			_portletPreferencesLocalService.getPreferences(
				portletLayout.getCompanyId(),
				PortletKeys.PREFS_OWNER_ID_DEFAULT,
				PortletKeys.PREFS_OWNER_TYPE_LAYOUT, portletLayout.getPlid(),
				newPortletId);

		Assert.assertEquals(
			String.valueOf(objectEntry.getObjectEntryId()),
			portletPreferences.getValue("objectEntryId", StringPool.BLANK));
	}

	private void _assertSuccessful(BackgroundTaskResult backgroundTaskResult) {
		Assert.assertEquals(
			backgroundTaskResult.getStatusMessage(),
			BackgroundTaskConstants.STATUS_SUCCESSFUL,
			backgroundTaskResult.getStatus());
	}

	private BackgroundTaskResult _execute(boolean rewritePages)
		throws Exception {

		Map<String, Serializable> taskContextMap =
			HashMapBuilder.<String, Serializable>put(
				"depotEntryId", _depotEntry.getDepotEntryId()
			).put(
				"migrateBlogsEntries", true
			).put(
				"migrateFileEntries", true
			).put(
				"migrateJournalArticles", true
			).put(
				"rewritePages", rewritePages
			).put(
				"sourceGroupId", _group.getGroupId()
			).build();

		long userId = TestPropsValues.getUserId();

		BackgroundTask backgroundTask =
			(BackgroundTask)ProxyUtil.newProxyInstance(
				BackgroundTask.class.getClassLoader(),
				new Class<?>[] {BackgroundTask.class},
				(proxy, method, arguments) -> {
					String methodName = method.getName();

					if (methodName.equals("getTaskContextMap")) {
						return taskContextMap;
					}
					else if (methodName.equals("getUserId")) {
						return userId;
					}

					return null;
				});

		return _backgroundTaskExecutor.execute(backgroundTask);
	}

	private ObjectEntry _getObjectEntry(
			String objectDefinitionExternalReferenceCode,
			String objectEntryExternalReferenceCode)
		throws Exception {

		ObjectDefinition objectDefinition =
			_objectDefinitionLocalService.
				getObjectDefinitionByExternalReferenceCode(
					objectDefinitionExternalReferenceCode,
					_group.getCompanyId());

		return _objectEntryLocalService.getObjectEntry(
			objectEntryExternalReferenceCode, _depotEntry.getGroupId(),
			objectDefinition.getObjectDefinitionId());
	}

	private static final String _CMS_CONTENT_DISPLAY_PORTLET_NAME =
		"com_liferay_cms_migration_web_internal_portlet_" +
			"CMSContentDisplayPortlet";

	@Inject(
		filter = "background.task.executor.class.name=com.liferay.cms.migration.web.internal.background.task.CMSMigrationBackgroundTaskExecutor"
	)
	private BackgroundTaskExecutor _backgroundTaskExecutor;

	@Inject
	private BlogsEntryLocalService _blogsEntryLocalService;

	private DepotEntry _depotEntry;

	@Inject
	private DepotEntryLocalService _depotEntryLocalService;

	@Inject
	private DLAppLocalService _dlAppLocalService;

	@Inject
	private FragmentEntryLinkLocalService _fragmentEntryLinkLocalService;

	@DeleteAfterTestRun
	private Group _group;

	private JournalArticle _journalArticle;

	@Inject
	private LayoutLocalService _layoutLocalService;

	@Inject
	private ObjectDefinitionLocalService _objectDefinitionLocalService;

	@Inject
	private ObjectEntryLocalService _objectEntryLocalService;

	@Inject
	private Portal _portal;

	@Inject
	private PortletPreferencesLocalService _portletPreferencesLocalService;

	@Inject
	private SegmentsExperienceLocalService _segmentsExperienceLocalService;

}