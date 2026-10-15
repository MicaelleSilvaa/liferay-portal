/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cms.migration.web.internal.application.list;

import com.liferay.application.list.BasePanelApp;
import com.liferay.application.list.PanelApp;
import com.liferay.application.list.constants.PanelCategoryKeys;
import com.liferay.cms.migration.web.internal.constants.CMSMigrationPortletKeys;
import com.liferay.portal.kernel.model.Portlet;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * @author Lino Alves
 */
@Component(
	property = {
		"panel.app.order:Integer=600",
		"panel.category.key=" + PanelCategoryKeys.CONTROL_PANEL_SITES
	},
	service = PanelApp.class
)
public class CMSMigrationPanelApp extends BasePanelApp {

	@Override
	public String getIcon() {
		return "import";
	}

	@Override
	public Portlet getPortlet() {
		return _portlet;
	}

	@Override
	public String getPortletId() {
		return CMSMigrationPortletKeys.CMS_MIGRATION;
	}

	@Reference(
		target = "(jakarta.portlet.name=" + CMSMigrationPortletKeys.CMS_MIGRATION + ")"
	)
	private Portlet _portlet;

}