package com.gentrack.utils;

import android.content.Context;
import android.util.Log;

import com.gentrack.models.Bill;
import com.gentrack.models.Customer;
import com.gentrack.models.Payment;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.draw.SolidLine;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.LineSeparator;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.element.Text;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class PdfGenerator {

    private static final String    TAG    = "PdfGenerator";
    private static final DeviceRgb ACCENT = new DeviceRgb(0xE9, 0x45, 0x60);
    private static final DeviceRgb GREEN  = new DeviceRgb(0x27, 0xAE, 0x60);
    private static final DeviceRgb ROW_BG = new DeviceRgb(0xF5, 0xF5, 0xF5);

    private PdfGenerator() {}

    /**
     * Generates a PDF bill saved to getExternalFilesDir(null)/bills/bill_[id]_[month].pdf.
     * Returns the File on success, null on failure.
     */
    public static File generateBillPdf(Context context, Bill bill,
                                       Customer customer, List<Payment> payments) {
        File dir = new File(context.getExternalFilesDir(null), "bills");
        if (!dir.exists() && !dir.mkdirs()) {
            Log.e(TAG, "Could not create bills directory");
            return null;
        }
        File pdfFile = new File(dir, "bill_" + bill.getId() + "_" + bill.getMonth() + ".pdf");

        Document document = null;
        try {
            PdfFont bold    = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
            PdfFont regular = PdfFontFactory.createFont(StandardFonts.HELVETICA);

            document = new Document(new PdfDocument(new PdfWriter(pdfFile)));

            // ── Header ──────────────────────────────────────────
            String dateStr = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                    .format(new Date());
            Table headerTable = new Table(UnitValue.createPercentArray(new float[]{65, 35}))
                    .useAllAvailableWidth();
            headerTable.addCell(new Cell().setBorder(Border.NO_BORDER)
                    .add(new Paragraph("GENTRACK")
                            .setFont(bold).setFontSize(22).setFontColor(ACCENT))
                    .add(new Paragraph("Generator Subscription Bill")
                            .setFont(regular).setFontSize(10).setFontColor(ColorConstants.GRAY)));
            headerTable.addCell(new Cell().setBorder(Border.NO_BORDER)
                    .add(new Paragraph("Generated: " + dateStr)
                            .setFont(regular).setFontSize(9).setFontColor(ColorConstants.GRAY)
                            .setTextAlignment(TextAlignment.RIGHT)));
            document.add(headerTable);
            document.add(divider(1.0f, 8, 10));

            // ── Customer Details ─────────────────────────────────
            document.add(sectionTitle("Customer Details", bold));
            if (customer != null) {
                addRow(document, bold, regular, "Name",     customer.getName());
                addRow(document, bold, regular, "Phone",    customer.getPhone());
                addRow(document, bold, regular, "Location", customer.getLocation());
                addRow(document, bold, regular, "Amps",     customer.getAmps() + " A");
                addRow(document, bold, regular, "Status",   customer.getStatus());
            }
            document.add(spacer());

            // ── Bill Breakdown ────────────────────────────────────
            document.add(sectionTitle("Bill Details", bold));
            addRow(document, bold, regular, "Month", bill.getMonth());
            String billModel = bill.getBillingModel();
            if (Constants.BILLING_MODEL_BASE_CONSUMPTION.equals(billModel)) {
                double consumptionCharge = bill.getConsumption() * bill.getPricePerAmp();
                double baseFee           = bill.getTotal() - consumptionCharge;
                addRow(document, bold, regular, "Billing Model", "Base + Consumption");
                addRow(document, bold, regular, "Amps",  bill.getAmps() + " A");
                addRow(document, bold, regular, "Base Fee",
                        String.format(Locale.getDefault(), "$%.2f", baseFee));
                addRow(document, bold, regular, "Consumption",
                        String.format(Locale.getDefault(), "%.2f kWh", bill.getConsumption()));
                addRow(document, bold, regular, "Rate",
                        String.format(Locale.getDefault(), "$%.4f / kWh", bill.getPricePerAmp()));
                addRow(document, bold, regular, "Consumption Charge",
                        String.format(Locale.getDefault(), "$%.2f", consumptionCharge));
            } else if (Constants.BILLING_MODEL_CONSUMPTION.equals(billModel)) {
                addRow(document, bold, regular, "Billing Model", "Consumption (kWh)");
                addRow(document, bold, regular, "Consumption",
                        String.format(Locale.getDefault(), "%.2f kWh", bill.getConsumption()));
                addRow(document, bold, regular, "Rate",
                        String.format(Locale.getDefault(), "$%.4f / kWh", bill.getPricePerAmp()));
            } else {
                addRow(document, bold, regular, "Billing Model", "Flat Rate");
                addRow(document, bold, regular, "Amps",  bill.getAmps() + " A");
                addRow(document, bold, regular, "Rate",
                        String.format(Locale.getDefault(), "$%.2f / month", bill.getPricePerAmp()));
            }
            addRow(document, bold, regular, "Subtotal",
                    String.format(Locale.getDefault(), "$%.2f", bill.getTotal()));
            addRow(document, bold, regular, "Previous Balance",
                    String.format(Locale.getDefault(), "$%.2f", bill.getPreviousBalance()));

            document.add(divider(0.5f, 6, 6));

            document.add(new Paragraph(
                    String.format(Locale.getDefault(), "Final Total:  $%.2f", bill.getFinalTotal()))
                    .setFont(bold).setFontSize(14).setFontColor(ACCENT).setMarginBottom(3));

            // Remaining balance
            double totalPaid = 0;
            if (payments != null) for (Payment p : payments) totalPaid += p.getAmountPaid();
            double remaining = Math.max(0, bill.getFinalTotal() - totalPaid);

            document.add(new Paragraph("Status: " + bill.getStatus())
                    .setFont(bold).setFontSize(11).setMarginBottom(3));
            document.add(new Paragraph(
                    String.format(Locale.getDefault(), "Remaining Balance:  $%.2f", remaining))
                    .setFont(bold).setFontSize(11)
                    .setFontColor(remaining > 0 ? ACCENT : GREEN)
                    .setMarginBottom(6));
            document.add(spacer());

            // ── Payment History ───────────────────────────────────
            document.add(sectionTitle("Payment History", bold));
            if (payments != null && !payments.isEmpty()) {
                Table table = new Table(UnitValue.createPercentArray(new float[]{40, 30, 30}))
                        .useAllAvailableWidth();
                for (String h : new String[]{"Date", "Amount Paid", "Balance After"}) {
                    table.addHeaderCell(new Cell()
                            .setBackgroundColor(ROW_BG).setPadding(6)
                            .add(new Paragraph(h).setFont(bold).setFontSize(10)));
                }
                for (Payment p : payments) {
                    table.addCell(dataCell(regular, p.getDate() != null ? p.getDate() : "—"));
                    table.addCell(dataCell(regular,
                            String.format(Locale.getDefault(), "$%.2f", p.getAmountPaid())));
                    table.addCell(dataCell(regular,
                            String.format(Locale.getDefault(), "$%.2f", p.getRemainingBalance())));
                }
                document.add(table);
            } else {
                document.add(new Paragraph("No payments recorded.")
                        .setFont(regular).setFontSize(10).setFontColor(ColorConstants.GRAY));
            }

            // ── Footer ────────────────────────────────────────────
            document.add(spacer());
            document.add(divider(0.3f, 4, 6));
            document.add(new Paragraph("GenTrack  •  Generator Subscription Management")
                    .setFont(regular).setFontSize(8).setFontColor(ColorConstants.LIGHT_GRAY)
                    .setTextAlignment(TextAlignment.CENTER));

            return pdfFile;

        } catch (IOException e) {
            Log.e(TAG, "PDF generation failed", e);
            if (pdfFile.exists()) pdfFile.delete();
            return null;
        } finally {
            if (document != null) {
                try { document.close(); } catch (Exception ignored) {}
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────

    private static Paragraph sectionTitle(String title, PdfFont bold) {
        return new Paragraph(title)
                .setFont(bold).setFontSize(12).setFontColor(ColorConstants.BLACK)
                .setMarginBottom(4);
    }

    private static LineSeparator divider(float lineWidth, float marginTop, float marginBottom) {
        return (LineSeparator) new LineSeparator(new SolidLine(lineWidth))
                .setMarginTop(marginTop).setMarginBottom(marginBottom);
    }

    private static Paragraph spacer() {
        return new Paragraph(" ").setFontSize(5);
    }

    private static Cell dataCell(PdfFont regular, String text) {
        return new Cell().setPadding(5)
                .add(new Paragraph(text).setFont(regular).setFontSize(10));
    }

    private static void addRow(Document doc, PdfFont bold, PdfFont regular,
                               String label, String value) {
        if (value == null || value.isEmpty()) return;
        doc.add(new Paragraph()
                .add(new Text(label + ":  ").setFont(bold))
                .add(new Text(value).setFont(regular))
                .setFontSize(10).setMarginBottom(3));
    }
}
