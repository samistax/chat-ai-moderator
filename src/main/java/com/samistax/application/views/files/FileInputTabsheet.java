package com.samistax.application.views.files;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Label;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.theme.lumo.LumoUtility;

public class FileInputTabsheet extends VerticalLayout {

    protected final String MIME_TYPE_JSON = new String("application/json");
    protected final String MIME_TYPE_PDF = new String("application/pdf");
    protected final String MIME_TYPE_CSV = new String("text/csv");

    public FileInputTabsheet() {
        setSpacing(false);
        setSizeFull();
        setJustifyContentMode(JustifyContentMode.CENTER);
        setDefaultHorizontalComponentAlignment(Alignment.CENTER);
        getStyle().set("text-align", "center");

    }
    protected Upload createFileImporter(MemoryBuffer buffer, String ...acceptedFileTypes) {

        Upload upload = new Upload(buffer);
        upload.setAcceptedFileTypes(acceptedFileTypes);

        upload.addFailedListener( event -> {
            Notification.show(event.getReason().getMessage()).setPosition(Notification.Position.MIDDLE);
        });
        upload.addFileRejectedListener( event -> {
            Notification.show(event.getErrorMessage()).setPosition(Notification.Position.MIDDLE);
        });

        upload.setDropLabel(new Label("Drop file here..."));
        return upload;
    }


    protected Component createStatComponent(String title,String content, Button button) {
        VaadinIcon icon = VaadinIcon.INFO;

        Span titelSpan = new Span(title);
        //h2.addClassNames(LumoUtility.FontWeight.NORMAL, LumoUtility.Margin.NONE, LumoUtility.TextColor.HEADER, LumoUtility.FontSize.XSMALL);
        titelSpan.addClassNames("metric-label");

        Span contentSpan = new Span(content);
        //h2.addClassNames(LumoUtility.FontWeight.NORMAL, LumoUtility.Margin.NONE, LumoUtility.TextColor.HEADER, LumoUtility.FontSize.XSMALL);
        contentSpan.addClassNames("metric-content");

        VerticalLayout layout = new VerticalLayout(titelSpan,contentSpan);
        if ( button != null ) {
            layout.add(button);
        }
        layout.addClassName(LumoUtility.Padding.LARGE);
        //layout.setPadding(false);
        //layout.setSpacing(false);
        return layout;
    }
}
