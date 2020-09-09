package com.github.appreciated.apexcharts.internal.contributor;

import java.util.ArrayList;
import java.util.List;

import com.vaadin.flow.osgi.support.OsgiVaadinContributor;
import com.vaadin.flow.osgi.support.OsgiVaadinStaticResource;
import org.apache.aries.blueprint.annotation.bean.Bean;
import org.apache.aries.blueprint.annotation.service.Service;

@Bean
@Service
public class ApexContributor implements OsgiVaadinContributor
{
    @Override public List<OsgiVaadinStaticResource> getContributions()
    {
        List<OsgiVaadinStaticResource> contributions = new ArrayList<>();

        contributions.add(OsgiVaadinStaticResource.create(
            "/META-INF/resources/frontend/com/github/appreciated/apexcharts/apexcharts-wrapper.html",
            "/frontend/com/github/appreciated/apexcharts/apexcharts-wrapper.html"
        ));
        contributions.add(OsgiVaadinStaticResource.create(
            "/META-INF/resources/frontend/com/github/appreciated/apexcharts/apexcharts-wrapper.js",
            "/frontend/com/github/appreciated/apexcharts/apexcharts-wrapper.js"
        ));

        return contributions;
    }
}
