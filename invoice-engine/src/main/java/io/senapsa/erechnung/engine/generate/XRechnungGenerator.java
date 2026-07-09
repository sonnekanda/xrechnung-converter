package io.senapsa.erechnung.engine.generate;

import org.mustangproject.CII.CIIToUBL;
import org.mustangproject.Invoice;
import org.mustangproject.ZUGFeRD.Profiles;
import org.mustangproject.ZUGFeRD.ZUGFeRD2PullProvider;
import org.springframework.stereotype.Component;

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

    /** CII-XML im Profil XRechnung (UN/CEFACT). */
    public byte[] generateCii(Invoice invoice) {
        ZUGFeRD2PullProvider provider = new ZUGFeRD2PullProvider();
        provider.setProfile(Profiles.getByName("XRechnung"));
        provider.generateXML(invoice);
        return provider.getXML();
    }

    /** UBL-XML (aus CII konvertiert). */
    public byte[] generateUbl(Invoice invoice) throws Exception {
        byte[] cii = generateCii(invoice);
        Path in = Files.createTempFile("erechnung-cii-", ".xml");
        Path out = Files.createTempFile("erechnung-ubl-", ".xml");
        try {
            Files.write(in, cii);
            new CIIToUBL().convert(in.toFile(), out.toFile());
            return Files.readAllBytes(out);
        } finally {
            deleteQuietly(in.toFile());
            deleteQuietly(out.toFile());
        }
    }

    public String asString(byte[] xml) {
        return new String(xml, StandardCharsets.UTF_8);
    }

    private static void deleteQuietly(File f) {
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }
}
