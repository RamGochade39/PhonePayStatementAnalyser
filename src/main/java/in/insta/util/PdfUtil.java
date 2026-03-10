package in.insta.util;

import java.io.InputStream;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

public class PdfUtil {

	public static String extractText(InputStream inputStream, String password) throws Exception {

		PDDocument document;

		try {
			if (password != null && !password.isEmpty()) {
				document = PDDocument.load(inputStream, password);
			} else {
				document = PDDocument.load(inputStream);
			}
		} catch (Exception e) {
			throw new RuntimeException("Invalid PDF password or corrupted file.");
		}

		PDFTextStripper stripper = new PDFTextStripper();
		String text = stripper.getText(document);

		document.close();

		// System.out.println(text);
		return text;
	}
}