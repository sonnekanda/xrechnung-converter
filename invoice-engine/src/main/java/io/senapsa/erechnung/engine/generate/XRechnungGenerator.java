package io.senapsa.erechnung.engine.generate;

import org.mustangproject.CII.CIIToUBL;
import org.mustangproject.Invoice;
import org.mustangproject.ZUGFeRD.Profiles;
import org.mustangproject.ZUGFeRD.ZUGFeRD2PullProvider;
import org.mustangproject.ZUGFeRD.ZUGFeRDExporterFromA1;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Erzeugt aus einem {@link Invoice} die XRechnung-XML.
 * Zielsyntax UBL (Vorgabe "UBL zuerst"): Mustangproject erzeugt zunaechst
 * CII (Profil XRechnung) und konvertiert nach UBL.
 */
@Component
public class XRechnungGenerator {

    // Offizielle XRechnung-3.0-Kennung (BT-24). Mustangs CII->UBL-Konvertierung
    // setzt eine PEPPOL-Kennung; fuer die amtliche KoSIT-Pruefung muss die
    // XRechnung-CustomizationID stehen (sonst "kein Szenario gefunden").
    private static final String XRECHNUNG_CUSTOMIZATION =
            "urn:cen.eu:en16931:2017#compliant#urn:xeinkauf.de:kosit:xrechnung_3.0";

    /** CII-XML im Profil XRechnung (UN/CEFACT). */
    public byte[] generateCii(Invoice invoice) {
        ZUGFeRD2PullProvider provider = new ZUGFeRD2PullProvider();
        provider.setProfile(Profiles.getByName("XRechnung"));
        provider.generateXML(invoice);
        return provider.getXML();
    }

    /**
     * UBL-XML (aus CII konvertiert), an die XRechnung-Anforderungen angepasst.
     * @param sellerEmail elektronische Adresse des Verkaeufers (BT-34), optional
     * @param buyerEmail  elektronische Adresse des Kaeufers (BT-49), optional
     */
    public byte[] generateUbl(Invoice invoice, String sellerEmail, String buyerEmail) throws Exception {
        byte[] cii = generateCii(invoice);
        Path in = Files.createTempFile("erechnung-cii-", ".xml");
        Path out = Files.createTempFile("erechnung-ubl-", ".xml");
        try {
            Files.write(in, cii);
            new CIIToUBL().convert(in.toFile(), out.toFile());
            String xml = new String(Files.readAllBytes(out), StandardCharsets.UTF_8);
            // CustomizationID auf die XRechnung-Kennung setzen (nur die erste,
            // dokumentweite Angabe; ClassifiedTaxCategory bleibt unberuehrt).
            xml = xml.replaceFirst(
                    "(<cbc:CustomizationID>)[^<]*(</cbc:CustomizationID>)",
                    "$1" + java.util.regex.Matcher.quoteReplacement(XRECHNUNG_CUSTOMIZATION) + "$2");
            // Elektronische Adressen (BT-34/BT-49) ergaenzen - XRechnung-Pflicht,
            // von Mustangs CII->UBL nicht erzeugt. EndpointID ist das erste
            // Element in cac:Party.
            xml = injectEndpoint(xml, "AccountingSupplierParty", sellerEmail);
            xml = injectEndpoint(xml, "AccountingCustomerParty", buyerEmail);
            return xml.getBytes(StandardCharsets.UTF_8);
        } finally {
            deleteQuietly(in.toFile());
            deleteQuietly(out.toFile());
        }
    }

    /** Fuegt {@code <cbc:EndpointID schemeID="EM">email</cbc:EndpointID>} als erstes
     *  Kind von cac:Party der genannten Partei ein (falls E-Mail vorhanden). */
    private static String injectEndpoint(String xml, String partyElement, String email) {
        if (email == null || email.isBlank()) {
            return xml;
        }
        String endpoint = "<cbc:EndpointID schemeID=\"EM\">"
                + email.replace("&", "&amp;").replace("<", "&lt;") + "</cbc:EndpointID>";
        return xml.replaceFirst(
                "(<cac:" + partyElement + ">\\s*<cac:Party>)",
                "$1" + java.util.regex.Matcher.quoteReplacement(endpoint));
    }

    /**
     * Erzeugt ein ZUGFeRD-/Factur-X-Hybrid-PDF: bettet die (bereits geprüfte)
     * XRechnung-CII als XML in das vorhandene Original-PDF ein. Es wird KEIN
     * sichtbares PDF neu erzeugt – nur das vorliegende PDF wird angereichert
     * (Best-Effort-Konvertierung nach PDF/A-3; Abas-PDFs sind kein PDF/A,
     * daher {@code ignorePDFAErrors}).
     *
     * @param sourcePdf Bytes des Original-PDFs (Pflicht – ohne PDF kein ZUGFeRD)
     */
    public byte[] generateZugferdPdf(Invoice invoice, byte[] sourcePdf) throws Exception {
        byte[] cii = generateCii(invoice);
        ZUGFeRDExporterFromA1 exporter = new ZUGFeRDExporterFromA1();
        // ignorePDFAErrors() MUSS vor load() gesetzt sein: load() prüft sonst die
        // PDF/A-1-Konformität und bricht ab. Abas-PDFs sind kein PDF/A -> Best-Effort.
        exporter.setProducer("senapsa eRechnung");
        exporter.setCreator("senapsa eRechnung");
        exporter.ignorePDFAErrors();
        exporter.load(sourcePdf);
        exporter.setProfile(Profiles.getByName("XRechnung"));
        exporter.setXML(cii);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exporter.export(out);
        return out.toByteArray();
    }

    public String asString(byte[] xml) {
        return new String(xml, StandardCharsets.UTF_8);
    }

    private static void deleteQuietly(File f) {
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }
}
