/*
 * Copyright (C) 2001-2026 Food and Agriculture Organization of the
 * United Nations (FAO-UN), United Nations World Food Programme (WFP)
 * and United Nations Environment Programme (UNEP)
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or (at
 * your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301, USA
 *
 * Contact: Jeroen Ticheler - FAO - Viale delle Terme di Caracalla 2,
 * Rome - Italy. email: geonetwork@osgeo.org
 */

package org.fao.geonet.schema;


import org.fao.geonet.utils.ResolverWrapper;
import org.fao.geonet.utils.TransformerFactoryFactory;
import org.fao.geonet.utils.Xml;
import org.jdom.Element;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Field;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Map;
import org.jdom.Content;
import org.jdom.Namespace;

import static org.fao.geonet.schema.TestSupport.getResource;

public class BuildEditorFormTest {

	private static final boolean GENERATE_EXPECTED_FILE = true;

	private static Field resolverMapField;

	@Before
	public void initSaxon() {
		TransformerFactoryFactory.init("net.sf.saxon.TransformerFactoryImpl");
	}

	@BeforeClass
	public static void initOasis() throws NoSuchFieldException, IllegalAccessException, URISyntaxException {
		resolverMapField = ResolverWrapper.class.getDeclaredField("resolverMap");
		resolverMapField.setAccessible(true);
		((Map<?, ?>) resolverMapField.get(null)).clear();

		String catFiles = getResource("gn-site/WEB-INF/oasis-catalog.xml") + ";" + addRequiredSchemasAndDisableConflictingOne();
		System.setProperty("jeeves.xml.catalog.files", catFiles);
		ResolverWrapper.createResolverForSchema("DEFAULT", null);
		ResolverWrapper.getInstance().setBlankXSLFile(getResource("config/blank.xsl").toAbsolutePath().toString());
	}

	@AfterClass
	public static void clearOasis() throws IllegalAccessException {
		((Map<?,?>) resolverMapField.get(null)).clear();
	}

	@Test
	public void rawUpperRhineCastlesEdit() throws Exception {
		Path xslFile = getResource("gn-site/xslt/ui-metadata/edit/edit.xsl");
		Path xmlFile = getResource("raw-UpperRhineCastles-inflated-for-edition.xml");
		Element inflatedMd = Xml.loadFile(xmlFile);

		Element editorForm = Xml.transform(inflatedMd, xslFile);

		XMLOutputter xmlOutputter = new XMLOutputter(Format.getPrettyFormat().setLineSeparator("\n"));
		String actual = xmlOutputter.outputString(editorForm);

		TestSupport.assertGeneratedDataByteMatchExpected("raw-UpperRhineCastles-editor-form.xml", actual, GENERATE_EXPECTED_FILE);
	}

	@Test
	public void rawUpperRhineCastlesEditWithReport() throws Exception {
		Path xslFile = getResource("gn-site/xslt/ui-metadata/edit/edit.xsl");
		Path xmlFile = getResource("raw-UpperRhineCastles-inflated-for-edition.xml");
		Element inflatedMd = Xml.loadFile(xmlFile);
		Element request = inflatedMd.getChild("request");
		if (request != null) {
			request.removeChildren("withvalidationerrors");
			request.removeChildren("showvalidationerrors");
			int insertIndex = Math.min(10, request.getContentSize());
			request.addContent(insertIndex, new Element("withvalidationerrors").setText("true"));
			request.addContent(insertIndex + 1, new Element("showvalidationerrors").setText("true"));
		}
		Element report = Xml.loadFile(getResource("report.xml"));
		Element mdMetadata = inflatedMd.getChild("MD_Metadata",
				Namespace.getNamespace("mdb", "http://standards.iso.org/iso/19115/-3/mdb/2.0"));
		if (mdMetadata == null) mdMetadata = inflatedMd;
		int lastGeonetAttributeIdx = mdMetadata.getContentSize();
		for (int i = mdMetadata.getContentSize() - 1; i >= 0; i--) {
			Content child = mdMetadata.getContent(i);
			if (child instanceof Element) {
				Element childEl = (Element) child;
				if ("http://www.fao.org/geonetwork".equals(childEl.getNamespaceURI())
						&& "attribute".equals(childEl.getName())) {
					lastGeonetAttributeIdx = i;
					break;
				}
			}
		}
		mdMetadata.addContent(lastGeonetAttributeIdx, (Element) report.clone());

		// Set lang2chars to "fr"
		Element gui = inflatedMd.getChild("gui");
		if (gui == null) {
			gui = new Element("gui");
			inflatedMd.addContent(0, gui);
		}
		Element lang2chars = gui.getChild("lang2chars");
		if (lang2chars == null) {
			lang2chars = new Element("lang2chars");
			gui.addContent(lang2chars);
		}
		lang2chars.setText("en");

		Element editorForm = Xml.transform(inflatedMd, xslFile);

		// Extract only the div with class="gn-validation-report"
		// Use recursive search since HTML elements have no namespace
		Element validationReport = findDivByClass(editorForm, "gn-validation-report");

		if (validationReport != null) {
			XMLOutputter xmlOutputter = new XMLOutputter(Format.getPrettyFormat().setLineSeparator("\n"));
			String actual = xmlOutputter.outputString(validationReport);

			TestSupport.assertGeneratedDataByteMatchExpected("control.xml", actual, GENERATE_EXPECTED_FILE);
		} else {
			throw new AssertionError("No validation report div found in the editor form");
		}
	}

	private static Element findDivByClass(Element element, String className) {
		// Check if current element is a div with the target class
		if ("div".equals(element.getName()) && element.getNamespace().getURI().isEmpty()) {
			String classAttr = element.getAttributeValue("class");
			if (classAttr != null && classAttr.contains(className)) {
				return element;
			}
		}

		// Search in children recursively
		@SuppressWarnings("unchecked")
		java.util.List<Element> children = element.getChildren();
		for (Element child : children) {
			Element found = findDivByClass(child, className);
			if (found != null) {
				return found;
			}
		}

		return null;
	}

	private static Path addRequiredSchemasAndDisableConflictingOne() throws URISyntaxException {
		return getResource("config/schemaplugin-uri-catalog.xml");
	}
}