package com.samistax.application.views.files;

import com.samistax.application.service.AstraVectorService;
import com.samistax.application.service.OpenAIClient;
import com.samistax.application.views.MainLayout;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.board.Board;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;
import org.springframework.beans.factory.annotation.Value;

import javax.annotation.security.PermitAll;


@PageTitle("File Assistant")
@Route(value = "files", layout = MainLayout.class)
@PermitAll
public class FileInputView extends VerticalLayout {

    @Value( "${astra.token}" )
    private String ASTRA_TOKEN;

    public FileInputView(OpenAIClient aiClient, AstraVectorService astraVector) {
        setSpacing(false);

        addClassName("files-view");
        TabSheet tabSheet = new TabSheet();
        tabSheet.add("PDF Assistant", new PDFTabsheet(aiClient, astraVector));
        add(tabSheet);

        setSizeFull();
        setJustifyContentMode(JustifyContentMode.START);
        setDefaultHorizontalComponentAlignment(Alignment.STRETCH);
        getStyle().set("text-align", "center");
    }
}
