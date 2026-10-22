/*
 * JasperReports - Free Java Reporting Library.
 * Copyright (C) 2001 - 2023 Cloud Software Group, Inc. All rights reserved.
 * http://www.jaspersoft.com
 *
 * Unless you have purchased a commercial license agreement from Jaspersoft,
 * the following license terms apply:
 *
 * This program is part of JasperReports.
 *
 * JasperReports is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * JasperReports is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with JasperReports. If not, see <http://www.gnu.org/licenses/>.
 */
package net.sf.jasperreports.search;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.sf.jasperreports.engine.JRConstants;

/**
 * @author Narcis Marcu (narcism@users.sourceforge.net)
 */
public class LuceneSpansInfo implements SpansInfo, Serializable {

	private static final long serialVersionUID = JRConstants.SERIAL_VERSION_UID;
	private final List<String> queryTerms;
	private final Map<String, List<HitSpanInfo>> hitSpanInfo;
	private final Map<String, Integer> hitSpansPerPage;

	public LuceneSpansInfo(List<String> queryTerms) {
		this.queryTerms = new ArrayList<>(queryTerms);
		this.hitSpanInfo = new LinkedHashMap<>();
		this.hitSpansPerPage = new LinkedHashMap<>();
	}

	@Override
	public List<String> getQueryTerms() {
		return queryTerms;
	}

	@Override
	public void addSpanInfo(String key, HitSpanInfo span) {
		if (!hitSpanInfo.containsKey(key)) {
			hitSpanInfo.put(key, new ArrayList<>());
		}
		hitSpanInfo.get(key).add(span);
		String pageNo = span.getPageNo();
		hitSpansPerPage.put(pageNo, hitSpansPerPage.containsKey(pageNo) ? hitSpansPerPage.get(pageNo) + 1 : 1);
	}

	@Override
	public boolean hasHitSpanInfo(String key) {
		return hitSpanInfo.containsKey(key);
	}

	@Override
	public List<HitSpanInfo> getHitSpanInfo(String key) {
		return hitSpanInfo.get(key);
	}

	@Override
	public Map<String, Integer> getHitSpansPerPage() {
		return hitSpansPerPage;
	}

	@Override
	public int getTermsPerQuery() {
		return queryTerms.size();
	}
}
