package io.senapsa.erechnung.webapp.service;

import io.senapsa.erechnung.webapp.domain.Invoice;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Übersetzt den technischen Validierungsbericht (Mustangproject- oder KoSIT-XML)
 * in eine menschenlesbare Zusammenfassung für die Oberfläche. Format-tolerant:
 * relevante Elemente werden über ihren lokalen Namen erkannt – unabhängig vom
 * Namespace, sodass sowohl das Mustang- als auch das KoSIT-/Schematron-Format
 * abgedeckt sind. Das Gültig-Urteil selbst stammt aus dem Rechnungsstatus
 * ({@link Invoice#isValid()}), nicht aus dem Bericht.
 */
@Service
public class ValidationReportSummarizer {

    /** Obergrenze, damit sehr große Berichte die Oberfläche nicht überfluten. */
    private static final int MAX_MELDUNGEN = 300;

    public Summary summarize(Invoice invoice) {
        List<Meldung> meldungen = new ArrayList<>();
        String report = invoice.getValidationReport();
        if (report != null && !report.isBlank()) {
            try {
                Document doc = parse(report);
                collect(doc.getDocumentElement(), meldungen, new HashSet<>());
            } catch (Exception ignored) {
                // Unlesbarer Bericht: leere Liste; das Urteil kommt aus invoice.valid.
            }
        }
        int fehler = 0, warnung = 0, hinweis = 0;
        for (Meldung m : meldungen) {
            switch (m.schwere()) {
                case "fehler" -> fehler++;
                case "warnung" -> warnung++;
                default -> hinweis++;
            }
        }
        return new Summary(invoice.isValid(), invoice.getRechnungsnummer(),
                new Anzahl(fehler, warnung, hinweis), meldungen);
    }

    private void collect(Node node, List<Meldung> out, Set<String> seen) {
        if (node == null || out.size() >= MAX_MELDUNGEN) {
            return;
        }
        for (Node c = node.getFirstChild(); c != null; c = c.getNextSibling()) {
            if (c.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element el = (Element) c;
            Meldung m = toMeldung(local(el), el);
            if (m != null && seen.add(m.schwere() + "|" + m.kontext() + "|" + m.text()) && out.size() < MAX_MELDUNGEN) {
                out.add(m);
            }
            collect(el, out, seen);
        }
    }

    /** Bildet ein bekanntes Meldungs-Element auf eine Meldung ab, sonst null. */
    private Meldung toMeldung(String localName, Element el) {
        String schwere;
        String kontext;
        String text;
        switch (localName) {
            case "message" -> {
                // Mustang: Attribut "type"; KoSIT-Report: Attribut "level".
                schwere = normSchwere(firstNonBlank(el.getAttribute("type"), el.getAttribute("level")), "hinweis");
                kontext = firstNonBlank(el.getAttribute("criterion"), el.getAttribute("id"),
                        el.getAttribute("code"), el.getAttribute("location"));
                text = textOf(el);
            }
            case "failed-assert" -> {
                // Schematron: fehlgeschlagene Zusicherung -> standardmäßig Fehler.
                schwere = normSchwere(firstNonBlank(el.getAttribute("flag"), el.getAttribute("role")), "fehler");
                kontext = firstNonBlank(el.getAttribute("id"), el.getAttribute("location"));
                text = childText(el, "text");
            }
            case "successful-report" -> {
                // Schematron: bewusst gemeldete Bedingung (i.d.R. Warnung/Hinweis).
                schwere = normSchwere(firstNonBlank(el.getAttribute("flag"), el.getAttribute("role")), "hinweis");
                kontext = firstNonBlank(el.getAttribute("id"), el.getAttribute("location"));
                text = childText(el, "text");
            }
            default -> {
                return null;
            }
        }
        if (text == null || text.isBlank()) {
            return null;
        }
        String k = kontext == null || kontext.isBlank() ? null : kontext.trim();
        return new Meldung(schwere, k, text.trim().replaceAll("\\s+", " "));
    }

    private static String normSchwere(String raw, String fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.contains("error") || s.contains("fatal") || s.contains("fehler")) {
            return "fehler";
        }
        if (s.contains("warn")) {
            return "warnung";
        }
        return "hinweis";
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    /** Nur direkter Textinhalt des Elements (ohne verschachtelte Element-Texte). */
    private static String textOf(Element el) {
        StringBuilder sb = new StringBuilder();
        for (Node c = el.getFirstChild(); c != null; c = c.getNextSibling()) {
            if (c.getNodeType() == Node.TEXT_NODE || c.getNodeType() == Node.CDATA_SECTION_NODE) {
                sb.append(c.getNodeValue());
            }
        }
        String s = sb.toString().trim();
        return s.isEmpty() ? null : s;
    }

    /** Text eines Kindelements mit gegebenem lokalen Namen; sonst gesamter Textinhalt. */
    private static String childText(Element el, String childLocalName) {
        for (Node c = el.getFirstChild(); c != null; c = c.getNextSibling()) {
            if (c.getNodeType() == Node.ELEMENT_NODE && childLocalName.equals(local((Element) c))) {
                String t = textOf((Element) c);
                if (t != null) {
                    return t;
                }
            }
        }
        return el.getTextContent() == null ? null : el.getTextContent().trim();
    }

    private static String local(Element el) {
        String ln = el.getLocalName();
        if (ln == null) {
            ln = el.getTagName();
            int i = ln.indexOf(':');
            if (i >= 0) {
                ln = ln.substring(i + 1);
            }
        }
        return ln;
    }

    private static Document parse(String xml) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        // Externe Entitäten/DTDs abschalten (XXE-Schutz).
        f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        f.setFeature("http://xml.org/sax/features/external-general-entities", false);
        f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        f.setExpandEntityReferences(false);
        DocumentBuilder b = f.newDocumentBuilder();
        return b.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    /** Menschenlesbare Zusammenfassung des Validierungsberichts. */
    public record Summary(boolean gueltig, String rechnungsnummer, Anzahl anzahl, List<Meldung> meldungen) {
    }

    public record Anzahl(int fehler, int warnung, int hinweis) {
    }

    /** schwere ∈ fehler|warnung|hinweis. */
    public record Meldung(String schwere, String kontext, String text) {
    }
}
