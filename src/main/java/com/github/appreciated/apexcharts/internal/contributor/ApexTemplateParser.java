package com.github.appreciated.apexcharts.internal.contributor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.vaadin.flow.component.dependency.HtmlImport;
import com.vaadin.flow.component.polymertemplate.PolymerTemplate;
import com.vaadin.flow.component.polymertemplate.TemplateParser;
import com.vaadin.flow.internal.AnnotationReader;
import com.vaadin.flow.internal.ReflectionCache;
import com.vaadin.flow.server.DependencyFilter;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.VaadinServletRequest;
import com.vaadin.flow.server.WebBrowser;
import com.vaadin.flow.server.startup.FakeBrowser;
import com.vaadin.flow.shared.ui.Dependency;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Comment;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default template parser implementation.
 * <p>
 * The implementation scans all HTML imports annotations for the given template class and tries to find the one that
 * contains template definition using the tag name.
 * <p>
 * The class is Singleton. Use {@link com.vaadin.flow.component.polymertemplate.DefaultTemplateParser#getInstance()} to
 * get its instance.
 *
 * @author Vaadin Ltd
 * @see TemplateParser
 * @since 1.0
 */
public final class ApexTemplateParser implements TemplateParser
{
    private static final ReflectionCache<PolymerTemplate<?>, AtomicBoolean> LOG_CACHE = new ReflectionCache<>(
        clazz -> new AtomicBoolean());

    private static final TemplateParser INSTANCE = new ApexTemplateParser();

    private ApexTemplateParser()
    {
        // Doesn't allow external instantiation
    }

    public static TemplateParser getInstance()
    {
        return INSTANCE;
    }

    @Override
    public TemplateData getTemplateContent(
        Class<? extends PolymerTemplate<?>> clazz, String tag,
        VaadinService service)
    {
        boolean logEnabled = LOG_CACHE.get(clazz).compareAndSet(false, true);
        WebBrowser browser = FakeBrowser.getEs6();

        List<Dependency> dependencies = AnnotationReader
            .getAnnotationsFor(clazz, HtmlImport.class).stream()
            .map(htmlImport -> new Dependency(Dependency.Type.HTML_IMPORT,
                htmlImport.value(), htmlImport.loadMode()))
            .collect(Collectors.toList());

        DependencyFilter.FilterContext filterContext = new DependencyFilter.FilterContext(service, browser);
        for (DependencyFilter filter : service.getDependencyFilters())
        {
            dependencies = filter.filter(new ArrayList<>(dependencies),
                filterContext);
        }

        for (Dependency dependency : dependencies)
        {
            if (dependency.getType() != Dependency.Type.HTML_IMPORT)
            {
                continue;
            }

            String url = dependency.getUrl();
            String requestUrl = ((VaadinServletRequest)VaadinService.getCurrentRequest())
                .getHttpServletRequest()
                .getRequestURL()
                .toString();
            if (requestUrl.endsWith("/")) {
                requestUrl = requestUrl.substring(0, requestUrl.length() - 1);
            }

            try (InputStream content = service.getResourceAsStream(requestUrl + url, browser,
                null))
            {
                if (content == null)
                {
                    throw new IllegalStateException(
                        String.format("Can't find resource '%s' "
                            + "via the servlet context", url));
                }
                Element templateElement = parseHtmlImport(content, url, tag);
                if (logEnabled && templateElement != null)
                {
                    getLogger().debug(
                        "Found a template file containing template "
                            + "definition for the tag '{}' by the path '{}'",
                        tag, url);
                }

                if (templateElement != null)
                {
                    return new TemplateData(url, templateElement);

                }
            }
            catch (IOException exception)
            {
                // ignore exception on close()
                if (logEnabled)
                {
                    getLogger().warn("Couldn't close template input stream",
                        exception);
                }
            }
        }
        throw new IllegalStateException(String.format("Couldn't find the "
                + "definition of the element with tag '%s' "
                + "in any template file declared using @'%s' annotations. "
                + "Check the availability of the template files in your WAR "
                + "file or provide alternative implementation of the "
                + "method getTemplateContent() which should return an element "
                + "representing the content of the template file", tag,
            HtmlImport.class.getSimpleName()));
    }

    private static Element parseHtmlImport(InputStream content, String path,
        String tag)
    {
        assert content != null;
        try
        {
            Document parsedDocument = Jsoup.parse(content,
                StandardCharsets.UTF_8.name(), "");
            Optional<Element> optionalDomModule = JsoupUtils
                .getDomModule(parsedDocument, tag);
            if (!optionalDomModule.isPresent())
            {
                return null;
            }
            Element domModule = optionalDomModule.get();
            JsoupUtils.removeCommentsRecursively(domModule);
            return domModule;
        }
        catch (IOException exception)
        {
            throw new RuntimeException(String.format(
                "Can't parse the template declared using '%s' path", path),
                exception);
        }
    }

    private Logger getLogger()
    {
        return LoggerFactory.getLogger(com.vaadin.flow.component.polymertemplate.DefaultTemplateParser.class.getName());
    }

}

/**
 * Utilities for JSOUP DOM manipulations.
 *
 * @author Vaadin Ltd
 *
 */
final class JsoupUtils {

    private JsoupUtils() {
        // Utility class
    }

    /**
     * Removes all comments from the {@code node} tree.
     *
     * @param node
     *            a Jsoup node
     */
    static void removeCommentsRecursively(Node node) {
        int i = 0;
        while (i < node.childNodeSize()) {
            Node child = node.childNode(i);
            if (child instanceof Comment) {
                child.remove();
            } else {
                removeCommentsRecursively(child);
                i++;
            }
        }
    }

    /**
     * Finds {@code "dom-module"} element inside the {@code parent}.
     * <p>
     * If {@code id} is provided then {@code "dom-module"} element is searched
     * with the given {@code id} value.
     *
     * @param parent
     *            the parent element
     * @param id
     *            optional id attribute value to search {@code "dom-module"}
     *            element, may be {@code null}
     * @return
     */
    static Optional<Element> getDomModule(Element parent, String id) {
        Stream<Element> stream = parent.getElementsByTag("dom-module").stream();
        if (id != null) {
            stream = stream.filter(element -> id.equals(element.id()));
        }
        return stream.findFirst();
    }

}
