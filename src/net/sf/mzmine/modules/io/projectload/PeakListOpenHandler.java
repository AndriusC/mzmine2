/*
 * Copyright 2006-2009 The MZmine 2 Development Team
 *
 * This file is part of MZmine 2.
 *
 * MZmine 2 is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * MZmine 2 is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * MZmine 2; if not, write to the Free Software Foundation, Inc., 51 Franklin
 * St, Fifth Floor, Boston, MA 02110-1301 USA
 */

package net.sf.mzmine.modules.io.projectload;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Hashtable;
import java.util.Vector;
import java.util.logging.Logger;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import net.sf.mzmine.data.DataPoint;
import net.sf.mzmine.data.IsotopePatternStatus;
import net.sf.mzmine.data.PeakList;
import net.sf.mzmine.data.PeakListAppliedMethod;
import net.sf.mzmine.data.PeakStatus;
import net.sf.mzmine.data.RawDataFile;
import net.sf.mzmine.data.Scan;
import net.sf.mzmine.data.impl.SimpleChromatographicPeak;
import net.sf.mzmine.data.impl.SimpleDataPoint;
import net.sf.mzmine.data.impl.SimpleIsotopePattern;
import net.sf.mzmine.data.impl.SimplePeakIdentity;
import net.sf.mzmine.data.impl.SimplePeakList;
import net.sf.mzmine.data.impl.SimplePeakListAppliedMethod;
import net.sf.mzmine.data.impl.SimplePeakListRow;
import net.sf.mzmine.modules.io.projectsave.PeakListElementName;
import net.sf.mzmine.util.Range;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import com.Ostermiller.util.Base64;

import de.schlichtherle.util.zip.ZipEntry;
import de.schlichtherle.util.zip.ZipFile;

class PeakListOpenHandler extends DefaultHandler {

	private Logger logger = Logger.getLogger(this.getClass().getName());

	private SimplePeakListRow buildingRow;
	private SimplePeakList buildingPeakList;

	private int peakColumnID, rawDataFileID, numOfMZpeaks, representativeScan,
			fragmentScan;
	private double mass, rt, area;
	private int[] scanNumbers;
	private double height;
	private double[] masses, intensities;
	private String peakStatus, peakListName, name, identityName, formula,
			identificationMethod, identityID;
	private boolean preferred;
	private String dateCreated;

	private StringBuffer charBuffer;

	private Vector<String> appliedMethods, appliedMethodParameters;
	private Vector<RawDataFile> currentPeakListDataFiles;

	private Vector<DataPoint> currentIsotopes;
	private IsotopePatternStatus currentIsotopePatternStatus;
	private int currentIsotopePatternCharge;
	private String currentIsotopePatternDescription;

	private Hashtable<Integer, RawDataFile> dataFilesIDMap;

	private int parsedRows, totalRows;

	private boolean canceled = false;

	PeakListOpenHandler() {
		charBuffer = new StringBuffer();
		appliedMethods = new Vector<String>();
		appliedMethodParameters = new Vector<String>();
		currentPeakListDataFiles = new Vector<RawDataFile>();
		currentIsotopes = new Vector<DataPoint>();
	}

	/**
	 * Load the peak list from the zip file reading the XML peak list file
	 */
	PeakList readPeakList(ZipFile zipFile, ZipEntry entry,
			Hashtable<Integer, RawDataFile> dataFilesIDMap) throws IOException,
			ParserConfigurationException, SAXException {

		this.dataFilesIDMap = dataFilesIDMap;

		InputStream peakListStream = zipFile.getInputStream(entry);

		// Parse the XML file
		SAXParserFactory factory = SAXParserFactory.newInstance();
		SAXParser saxParser = factory.newSAXParser();
		saxParser.parse(peakListStream, this);

		return buildingPeakList;

	}

	/**
	 * @return the progress of these functions loading the peak list from the
	 *         zip file.
	 */
	double getProgress() {
		if (totalRows == 0)
			return 0;
		return (double) parsedRows / totalRows;
	}

	void cancel() {
		canceled = true;
	}

	/**
	 * @see org.xml.sax.helpers.DefaultHandler#startElement(java.lang.String,
	 *      java.lang.String, java.lang.String, org.xml.sax.Attributes)
	 */
	public void startElement(String namespaceURI, String lName, String qName,
			Attributes attrs) throws SAXException {

		if (canceled)
			throw new SAXException("Parsing canceled");

		// <ROW>
		if (qName.equals(PeakListElementName.ROW.getElementName())) {

			if (buildingPeakList == null) {
				initializePeakList();
			}
			int rowID = Integer.parseInt(attrs.getValue(PeakListElementName.ID
					.getElementName()));
			buildingRow = new SimplePeakListRow(rowID);
		}

		// <PEAK_IDENTITY>
		if (qName.equals(PeakListElementName.PEAK_IDENTITY.getElementName())) {
			identityID = attrs
					.getValue(PeakListElementName.ID.getElementName());
			preferred = Boolean.parseBoolean(attrs
					.getValue(PeakListElementName.PREFERRED.getElementName()));
		}

		// <PEAK>
		if (qName.equals(PeakListElementName.PEAK.getElementName())) {

			peakColumnID = Integer.parseInt(attrs
					.getValue(PeakListElementName.COLUMN.getElementName()));
			mass = Double.parseDouble(attrs.getValue(PeakListElementName.MZ
					.getElementName()));
			rt = Double.parseDouble(attrs.getValue(PeakListElementName.RT
					.getElementName()));
			height = Double.parseDouble(attrs
					.getValue(PeakListElementName.HEIGHT.getElementName()));
			area = Double.parseDouble(attrs.getValue(PeakListElementName.AREA
					.getElementName()));
			peakStatus = attrs.getValue(PeakListElementName.STATUS
					.getElementName());
		}

		// <MZPEAK>
		if (qName.equals(PeakListElementName.MZPEAKS.getElementName())) {
			numOfMZpeaks = Integer.parseInt(attrs
					.getValue(PeakListElementName.QUANTITY.getElementName()));
		}

		// <ISOTOPE_PATTERN>
		if (qName.equals(PeakListElementName.ISOTOPE_PATTERN.getElementName())) {
			currentIsotopes.clear();
			currentIsotopePatternStatus = IsotopePatternStatus.valueOf(attrs
					.getValue(PeakListElementName.STATUS.getElementName()));
			currentIsotopePatternCharge = Integer.valueOf(attrs
					.getValue(PeakListElementName.CHARGE.getElementName()));
			currentIsotopePatternDescription = attrs
					.getValue(PeakListElementName.DESCRIPTION.getElementName());
		}

	}

	/**
	 * @see org.xml.sax.helpers.DefaultHandler#endElement(java.lang.String,
	 *      java.lang.String, java.lang.String)
	 */
	public void endElement(String namespaceURI, String sName, String qName)
			throws SAXException {

		if (canceled)
			throw new SAXException("Parsing canceled");

		// <NAME>
		if (qName.equals(PeakListElementName.PEAKLIST_NAME.getElementName())) {
			name = getTextOfElement();
			logger.info("Loading peak list: " + name);
			peakListName = name;
		}

		// <PEAKLIST_DATE>
		if (qName.equals(PeakListElementName.PEAKLIST_DATE.getElementName())) {
			dateCreated = getTextOfElement();
		}

		// <QUANTITY>
		if (qName.equals(PeakListElementName.QUANTITY.getElementName())) {
			String text = getTextOfElement();
			totalRows = Integer.parseInt(text);
		}

		// <RAW_FILE>
		if (qName.equals(PeakListElementName.RAWFILE.getElementName())) {
			rawDataFileID = Integer.parseInt(getTextOfElement());
			RawDataFile dataFile = dataFilesIDMap.get(rawDataFileID);
			currentPeakListDataFiles.add(dataFile);
		}

		// <SCAN_ID>
		if (qName.equals(PeakListElementName.SCAN_ID.getElementName())) {

			byte[] bytes = Base64.decodeToBytes(getTextOfElement());
			// make a data input stream
			DataInputStream dataInputStream = new DataInputStream(
					new ByteArrayInputStream(bytes));
			scanNumbers = new int[numOfMZpeaks];
			for (int i = 0; i < numOfMZpeaks; i++) {
				try {
					scanNumbers[i] = dataInputStream.readInt();
				} catch (IOException ex) {
					throw new SAXException(ex);
				}
			}
		}

		// <REPRESENTATIVE_SCAN>
		if (qName.equals(PeakListElementName.REPRESENTATIVE_SCAN
				.getElementName())) {
			representativeScan = Integer.valueOf(getTextOfElement());
		}

		// <FRAGMENT_SCAN>

		if (qName.equals(PeakListElementName.FRAGMENT_SCAN.getElementName())) {
			fragmentScan = Integer.valueOf(getTextOfElement());
		}

		// <MASS>
		if (qName.equals(PeakListElementName.MZ.getElementName())) {

			byte[] bytes = Base64.decodeToBytes(getTextOfElement());
			// make a data input stream
			DataInputStream dataInputStream = new DataInputStream(
					new ByteArrayInputStream(bytes));
			masses = new double[numOfMZpeaks];
			for (int i = 0; i < numOfMZpeaks; i++) {
				try {
					masses[i] = (double) dataInputStream.readFloat();
				} catch (IOException ex) {
					throw new SAXException(ex);
				}
			}
		}

		// <HEIGHT>
		if (qName.equals(PeakListElementName.HEIGHT.getElementName())) {

			byte[] bytes = Base64.decodeToBytes(getTextOfElement());
			// make a data input stream
			DataInputStream dataInputStream = new DataInputStream(
					new ByteArrayInputStream(bytes));
			intensities = new double[numOfMZpeaks];
			for (int i = 0; i < numOfMZpeaks; i++) {
				try {
					intensities[i] = (double) dataInputStream.readFloat();
				} catch (IOException ex) {
					throw new SAXException(ex);
				}
			}
		}

		// <FORMULA>
		if (qName.equals(PeakListElementName.FORMULA.getElementName())) {
			formula = getTextOfElement();
		}

		// <IDENTITY_NAME>
		if (qName.equals(PeakListElementName.IDENTITY_NAME.getElementName())) {
			identityName = getTextOfElement();
		}

		// <IDENTIFICATION>
		if (qName.equals(PeakListElementName.IDENTIFICATION_METHOD
				.getElementName())) {
			identificationMethod = getTextOfElement();
		}

		// <PEAK>
		if (qName.equals(PeakListElementName.PEAK.getElementName())) {

			DataPoint[] mzPeaks = new DataPoint[numOfMZpeaks];
			Range peakRTRange = null, peakMZRange = null, peakIntensityRange = null;
			RawDataFile dataFile = dataFilesIDMap.get(peakColumnID);

			for (int i = 0; i < numOfMZpeaks; i++) {

				Scan sc = dataFile.getScan(scanNumbers[i]);
				double retentionTime = sc.getRetentionTime();

				double mz = masses[i];
				double intensity = intensities[i];

				if (i == 0) {
					peakRTRange = new Range(retentionTime);
					peakIntensityRange = new Range(intensity);
				} else {
					peakRTRange.extendRange(retentionTime);
					peakIntensityRange.extendRange(intensity);
				}
				if (mz > 0.0) {
					mzPeaks[i] = new SimpleDataPoint(mz, intensity);
					if (peakMZRange == null)
						peakMZRange = new Range(mz);
					else
						peakMZRange.extendRange(mz);
				}
			}

			PeakStatus status = PeakStatus.valueOf(peakStatus);

			SimpleChromatographicPeak peak = new SimpleChromatographicPeak(
					dataFile, mass, rt, height, area, scanNumbers, mzPeaks,
					status, representativeScan, fragmentScan, peakRTRange,
					peakMZRange, peakIntensityRange);

			if (currentIsotopes.size() > 0) {
				SimpleIsotopePattern newPattern = new SimpleIsotopePattern(
						currentIsotopePatternCharge, currentIsotopes
								.toArray(new DataPoint[0]),
						currentIsotopePatternStatus,
						currentIsotopePatternDescription);
				peak.setIsotopePattern(newPattern);
				currentIsotopes.clear();
			}

			buildingRow.addPeak(dataFile, peak);

		}

		// <PEAK_IDENTITY>
		if (qName.equals(PeakListElementName.PEAK_IDENTITY.getElementName())) {

			SimplePeakIdentity identity = new SimplePeakIdentity(identityID,
					identityName, new String[0], formula, null,
					identificationMethod);
			buildingRow.addPeakIdentity(identity, preferred);
		}

		// <ROW>
		if (qName.equals(PeakListElementName.ROW.getElementName())) {

			buildingPeakList.addRow(buildingRow);
			buildingRow = null;
			parsedRows++;
		}

		// <ISOTOPE>
		if (qName.equals(PeakListElementName.ISOTOPE.getElementName())) {
			String text = getTextOfElement();
			String items[] = text.split(":");
			double mz = Double.valueOf(items[0]);
			double intensity = Double.valueOf(items[1]);
			DataPoint isotope = new SimpleDataPoint(mz, intensity);
			currentIsotopes.add(isotope);
		}

		if (qName.equals(PeakListElementName.METHOD_NAME.getElementName())) {
			String appliedMethod = getTextOfElement();
			appliedMethods.add(appliedMethod);
		}

		if (qName
				.equals(PeakListElementName.METHOD_PARAMETERS.getElementName())) {
			String appliedMethodParam = getTextOfElement();
			appliedMethodParameters.add(appliedMethodParam);
		}

	}

	/**
	 * Return a string without tab an EOF characters
	 * 
	 * @return String element text
	 */
	private String getTextOfElement() {
		String text = charBuffer.toString();
		text = text.replaceAll("[\n\r\t]+", "");
		text = text.replaceAll("^\\s+", "");
		charBuffer.setLength(0);
		return text;
	}

	/**
	 * characters()
	 * 
	 * @see org.xml.sax.ContentHandler#characters(char[], int, int)
	 */
	public void characters(char buf[], int offset, int len) throws SAXException {
		charBuffer = charBuffer.append(buf, offset, len);
	}

	/**
	 * Initializes the peak list
	 */
	private void initializePeakList() {

		RawDataFile[] dataFiles = currentPeakListDataFiles
				.toArray(new RawDataFile[0]);

		buildingPeakList = new SimplePeakList(peakListName, dataFiles);

		for (int i = 0; i < appliedMethods.size(); i++) {
			String methodName = appliedMethods.elementAt(i);
			String methodParams = appliedMethodParameters.elementAt(i);
			PeakListAppliedMethod pam = new SimplePeakListAppliedMethod(
					methodName, methodParams);
			buildingPeakList.addDescriptionOfAppliedTask(pam);
		}
		buildingPeakList.setDateCreated(dateCreated);
	}
}