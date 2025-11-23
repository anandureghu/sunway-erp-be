package com.erp.service.pdf;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;

@Service
public class InvoicePDFService {

    public byte[] generateInvoicePdf(
            String invoiceId,
            String toParty,
            String description,
            String amount
    ) throws Exception {

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document();
        PdfWriter.getInstance(doc, out);

        doc.open();
        doc.add(new Paragraph("INVOICE", new Font(Font.HELVETICA, 20, Font.BOLD)));
        doc.add(new Paragraph("Invoice ID: " + invoiceId));
        doc.add(new Paragraph("To: " + toParty));
        doc.add(new Paragraph("Description: " + description));
        doc.add(new Paragraph("Amount: " + amount));
        doc.add(new Paragraph("Date: " + java.time.LocalDate.now()));

        doc.close();
        return out.toByteArray();
    }
}
