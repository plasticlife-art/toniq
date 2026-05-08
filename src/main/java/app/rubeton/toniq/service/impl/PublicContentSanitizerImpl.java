package app.rubeton.toniq.service.impl;

import app.rubeton.toniq.service.PublicContentSanitizer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Service;

@Service
public class PublicContentSanitizerImpl implements PublicContentSanitizer {

    private static final Safelist DESCRIPTION_HTML_SAFELIST = Safelist.none()
            .addTags("p", "br", "strong", "b", "em", "i", "ul", "ol", "li", "a")
            .addAttributes("a", "href")
            .addProtocols("a", "href", "http", "https", "mailto");

    @Override
    public String sanitizeDescriptionHtml(final String rawHtml) {
        if (rawHtml == null || rawHtml.isBlank()) {
            return "";
        }

        String sanitizedHtml = Jsoup.clean(rawHtml, "", DESCRIPTION_HTML_SAFELIST, new Document.OutputSettings().prettyPrint(false));
        Document document = Jsoup.parseBodyFragment(sanitizedHtml);
        document.outputSettings().prettyPrint(false);
        document.select("a[href]").forEach(link -> {
            link.attr("target", "_blank");
            link.attr("rel", "noopener noreferrer");
        });
        return document.body().html();
    }
}
