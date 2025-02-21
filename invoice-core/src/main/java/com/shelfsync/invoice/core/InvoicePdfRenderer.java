package com.shelfsync.invoice.core;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Currency;
import java.util.Locale;

/** Renders an {@link InvoiceRequest} to a single A4 PDF using OpenPDF. Stateless and thread-safe. */
public class InvoicePdfRenderer {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
            .withZone(ZoneOffset.UTC);
    private static final Color HEADER_BG = new Color(0x2F, 0x3E, 0x4E);
    private static final Color ROW_ALT = new Color(0xF3, 0xF5, 0xF7);

    private final Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, Color.DARK_GRAY);
    private final Font bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
    private final Font normal = FontFactory.getFont(FontFactory.HELVETICA, 10);
    private final Font small = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY);
    private final Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE);

    public byte[] render(InvoiceRequest request) {
        request.validate();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 48, 48, 48, 48);
        try {
            PdfWriter.getInstance(doc, out);
            doc.addTitle("Invoice " + request.getInvoiceNumber());
            doc.addCreator("ShelfSync");
            doc.open();
            addHeader(doc, request);
            addParties(doc, request);
            addLines(doc, request);
            addTotals(doc, request);
            Paragraph footer = new Paragraph("Thank you for your business.", small);
            footer.setSpacingBefore(24);
            doc.add(footer);
        } catch (DocumentException e) {
            throw new IllegalStateException("Failed to render invoice " + request.getInvoiceNumber(), e);
        } finally {
            if (doc.isOpen()) {
                doc.close();
            }
        }
        return out.toByteArray();
    }

    private void addHeader(Document doc, InvoiceRequest r) throws DocumentException {
        PdfPTable header = new PdfPTable(new float[]{1, 1});
        header.setWidthPercentage(100);
        header.addCell(borderless(new Phrase("INVOICE", title), Element.ALIGN_LEFT));

        Phrase meta = new Phrase();
        meta.add(new Chunk("Invoice #: ", bold));
        meta.add(new Chunk(r.getInvoiceNumber() + "\n", normal));
        if (r.getOrderNumber() != null) {
            meta.add(new Chunk("Order #: ", bold));
            meta.add(new Chunk(r.getOrderNumber() + "\n", normal));
        }
        meta.add(new Chunk("Date: ", bold));
        meta.add(new Chunk(DATE.format(Instant.parse(r.getIssuedAt())), normal));
        header.addCell(borderless(meta, Element.ALIGN_RIGHT));
        doc.add(header);
    }

    private void addParties(Document doc, InvoiceRequest r) throws DocumentException {
        PdfPTable parties = new PdfPTable(new float[]{1, 1});
        parties.setWidthPercentage(100);
        parties.setSpacingBefore(20);
        parties.addCell(borderless(party("From", r.getSeller()), Element.ALIGN_LEFT));
        parties.addCell(borderless(party("Bill to", r.getCustomer()), Element.ALIGN_LEFT));
        doc.add(parties);
    }

    private Phrase party(String label, InvoiceRequest.Party p) {
        Phrase phrase = new Phrase();
        phrase.add(new Chunk(label + "\n", small));
        if (p == null) {
            return phrase;
        }
        phrase.add(new Chunk(nullToEmpty(p.getName()) + "\n", bold));
        if (p.getAddress() != null && !p.getAddress().isBlank()) {
            phrase.add(new Chunk(p.getAddress() + "\n", normal));
        }
        if (p.getEmail() != null && !p.getEmail().isBlank()) {
            phrase.add(new Chunk(p.getEmail(), normal));
        }
        return phrase;
    }

    private void addLines(Document doc, InvoiceRequest r) throws DocumentException {
        PdfPTable table = new PdfPTable(new float[]{2.2f, 5f, 1.2f, 2f, 2f});
        table.setWidthPercentage(100);
        table.setSpacingBefore(24);
        table.setHeaderRows(1);
        for (String h : new String[]{"SKU", "Description", "Qty", "Unit price", "Amount"}) {
            PdfPCell cell = new PdfPCell(new Phrase(h, headerFont));
            cell.setBackgroundColor(HEADER_BG);
            cell.setPadding(6);
            cell.setBorder(Rectangle.NO_BORDER);
            cell.setHorizontalAlignment(h.equals("SKU") || h.equals("Description") ? Element.ALIGN_LEFT : Element.ALIGN_RIGHT);
            table.addCell(cell);
        }
        int row = 0;
        for (InvoiceRequest.Line line : r.getLines()) {
            Color bg = (row++ % 2 == 1) ? ROW_ALT : Color.WHITE;
            table.addCell(bodyCell(nullToEmpty(line.getSku()), Element.ALIGN_LEFT, bg));
            table.addCell(bodyCell(nullToEmpty(line.getDescription()), Element.ALIGN_LEFT, bg));
            table.addCell(bodyCell(Integer.toString(line.getQuantity()), Element.ALIGN_RIGHT, bg));
            table.addCell(bodyCell(money(line.getUnitPrice(), r.getCurrency()), Element.ALIGN_RIGHT, bg));
            BigDecimal amount = line.getLineTotal() != null ? line.getLineTotal()
                    : line.getUnitPrice().multiply(BigDecimal.valueOf(line.getQuantity()));
            table.addCell(bodyCell(money(amount, r.getCurrency()), Element.ALIGN_RIGHT, bg));
        }
        doc.add(table);
    }

    private void addTotals(Document doc, InvoiceRequest r) throws DocumentException {
        PdfPTable totals = new PdfPTable(new float[]{3, 1.6f});
        totals.setWidthPercentage(45);
        totals.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totals.setSpacingBefore(12);
        if (r.getSubtotal() != null) {
            addTotalRow(totals, "Subtotal", money(r.getSubtotal(), r.getCurrency()), normal);
        }
        if (r.getTax() != null) {
            String label = r.getTaxRate() == null ? "Tax"
                    : "Tax (" + r.getTaxRate().multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString() + "%)";
            addTotalRow(totals, label, money(r.getTax(), r.getCurrency()), normal);
        }
        addTotalRow(totals, "Total", money(r.getTotal(), r.getCurrency()), bold);
        doc.add(totals);
    }

    private void addTotalRow(PdfPTable table, String label, String value, Font font) {
        table.addCell(borderless(new Phrase(label, font), Element.ALIGN_RIGHT));
        table.addCell(borderless(new Phrase(value, font), Element.ALIGN_RIGHT));
    }

    private PdfPCell bodyCell(String text, int align, Color bg) {
        PdfPCell cell = new PdfPCell(new Phrase(text, normal));
        cell.setPadding(5);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setBackgroundColor(bg);
        cell.setHorizontalAlignment(align);
        return cell;
    }

    private PdfPCell borderless(Phrase phrase, int align) {
        PdfPCell cell = new PdfPCell(phrase);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setHorizontalAlignment(align);
        cell.setPaddingBottom(4);
        return cell;
    }

    static String money(BigDecimal amount, String currencyCode) {
        if (amount == null) {
            return "";
        }
        String symbol;
        try {
            symbol = Currency.getInstance(currencyCode).getSymbol(Locale.US);
        } catch (IllegalArgumentException | NullPointerException e) {
            symbol = currencyCode == null ? "" : currencyCode + " ";
        }
        return symbol + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
