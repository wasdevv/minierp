import java.io.File;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;

/** Renders the first page of a PDF to PNG: java -cp <pdfbox> scripts/RenderPdf.java in.pdf out.png */
public class RenderPdf {

    public static void main(String[] args) throws Exception {
        try (var document = Loader.loadPDF(new File(args[0]))) {
            ImageIO.write(new PDFRenderer(document).renderImageWithDPI(0, 110), "png", new File(args[1]));
        }
    }
}
