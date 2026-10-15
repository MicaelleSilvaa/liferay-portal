/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.cms.migration.web.internal.migration;

import com.liferay.portal.kernel.json.JSONArray;
import com.liferay.portal.kernel.json.JSONFactoryUtil;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.json.JSONUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * @author Lino Alves
 */
public class CMSMigrationReport {

	public static CMSMigrationReport of(JSONObject jsonObject) {
		CMSMigrationReport cmsMigrationReport = new CMSMigrationReport();

		if (jsonObject == null) {
			return cmsMigrationReport;
		}

		JSONObject countsJSONObject = jsonObject.getJSONObject("counts");

		if (countsJSONObject != null) {
			for (String key : countsJSONObject.keySet()) {
				cmsMigrationReport._counts.put(
					key, countsJSONObject.getLong(key));
			}
		}

		JSONArray errorsJSONArray = jsonObject.getJSONArray("errors");

		if (errorsJSONArray != null) {
			for (int i = 0; i < errorsJSONArray.length(); i++) {
				cmsMigrationReport._errors.add(errorsJSONArray.getString(i));
			}
		}

		JSONArray warningsJSONArray = jsonObject.getJSONArray("warnings");

		if (warningsJSONArray != null) {
			for (int i = 0; i < warningsJSONArray.length(); i++) {
				cmsMigrationReport._warnings.add(
					warningsJSONArray.getString(i));
			}
		}

		return cmsMigrationReport;
	}

	public synchronized void addError(String error) {
		_errors.add(error);
	}

	public synchronized void addWarning(String warning) {
		_warnings.add(warning);
	}

	public Map<String, Long> getCounts() {
		return _counts;
	}

	public List<String> getErrors() {
		return _errors;
	}

	public List<String> getWarnings() {
		return _warnings;
	}

	public boolean hasErrors() {
		return !_errors.isEmpty();
	}

	public synchronized void increment(String key) {
		_counts.merge(key, 1L, Long::sum);
	}

	public JSONObject toJSONObject() {
		JSONObject countsJSONObject = JSONFactoryUtil.createJSONObject();

		for (Map.Entry<String, Long> entry : _counts.entrySet()) {
			countsJSONObject.put(entry.getKey(), entry.getValue());
		}

		return JSONUtil.put(
			"counts", countsJSONObject
		).put(
			"errors", JSONUtil.putAll(_errors.toArray())
		).put(
			"warnings", JSONUtil.putAll(_warnings.toArray())
		);
	}

	private final Map<String, Long> _counts = new TreeMap<>();
	private final List<String> _errors = new ArrayList<>();
	private final List<String> _warnings = new ArrayList<>();

}