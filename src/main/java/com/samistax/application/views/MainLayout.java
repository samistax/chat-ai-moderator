package com.samistax.application.views;

import com.samistax.application.component.ColorPicker;
import com.samistax.application.views.chat.ChatView;
import com.samistax.application.views.files.FileInputView;
import com.samistax.application.views.userlist.UserListView;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.Unit;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.dom.ThemeList;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.theme.lumo.Lumo;
import com.vaadin.flow.theme.lumo.LumoUtility;
import org.vaadin.lineawesome.LineAwesomeIcon;

import javax.swing.plaf.ButtonUI;
import java.net.URL;

/**
 * The main view is a top-level placeholder for other views.
 */
@JsModule("prefers-color-scheme.js")
public class MainLayout extends AppLayout {

    private H2 viewTitle;
    private Image logo = new Image();

    public MainLayout() {
        setPrimarySection(Section.DRAWER);
        addDrawerContent();
        addHeaderContent();
    }

    private void addHeaderContent() {
        DrawerToggle toggle = new DrawerToggle();
        toggle.setAriaLabel("Menu toggle");

        viewTitle = new H2();
        viewTitle.addClassNames(LumoUtility.FontSize.LARGE, LumoUtility.Margin.NONE);

        addToNavbar(true, toggle, viewTitle);
    }

    private void addDrawerContent() {
        H1 appName = new H1("Chat AI Moderator");
        appName.addClassNames(LumoUtility.FontSize.LARGE, LumoUtility.Margin.NONE);

        Button settingsBtn = new Button("", VaadinIcon.COG.create());
        settingsBtn.addClickListener(event -> openSettingsDialog());

        Header header = new Header(appName,  settingsBtn);
        Scroller scroller = new Scroller(createNavigation());

        addToDrawer(header, scroller, createFooter());
    }
    private void openSettingsDialog() {
        // Create the dialog
        Dialog settingsDialog = new Dialog();
        settingsDialog.setModal(true); // Set the dialog as modal    // Add the content to the dialog
        settingsDialog.setWidth(50, Unit.PERCENTAGE);
        VerticalLayout dialogContent = new VerticalLayout();
        ColorPicker colorPicker = new ColorPicker();
        colorPicker.addValueChangeListener(e -> {
            this.getStyle().setBackground(colorPicker.getValue());
        });

        TextField logoSource = new TextField("Company Logo");
        logoSource.setValue(logo.getSrc());
        logoSource.setWidthFull();
        logoSource.addValueChangeListener(e -> {
            try {
                //Image image = new Image(e.getValue(), "Company Logo");
                URL testConenction = new URL(e.getValue());
                if ( testConenction.getContent() != null ) {
                    logo.setSrc(e.getValue());
                }
            } catch (Exception ex) {
                Notification.show("Not a valid image URL: " + ex.getMessage(), 5000, Notification.Position.MIDDLE);
            }
        });
        Button toggleButton = new Button("Toggle Theme", click -> {
            ThemeList themeList = UI.getCurrent().getElement().getThemeList();
            if (themeList.contains(Lumo.DARK)) {
                themeList.remove(Lumo.DARK);
            } else {
                themeList.add(Lumo.DARK);
            }
        });
        toggleButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button closeButton = new Button("Close", click -> {
            settingsDialog.close();
        });
        closeButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);

        dialogContent.add(toggleButton);
        dialogContent.add(new Span("Theme Base Color"));
        dialogContent.add(colorPicker);
        dialogContent.add(logoSource);
        dialogContent.add(closeButton);
        settingsDialog.add(dialogContent);

        // Show the dialog
        settingsDialog.open();
    }
    private SideNav createNavigation() {
        SideNav nav = new SideNav();
        nav.addItem(new SideNavItem("User List", UserListView.class, LineAwesomeIcon.USER_FRIENDS_SOLID.create()));
        nav.addItem(new SideNavItem("Chat", ChatView.class, LineAwesomeIcon.COMMENTS.create()));
        nav.addItem(new SideNavItem("File Assistant", FileInputView.class, LineAwesomeIcon.FILE.create()));
        return nav;
    }

    private Footer createFooter() {
        logo.setWidth("225px");
        Footer layout = new Footer(logo);
        return layout;
    }

    @Override
    protected void afterNavigation() {
        super.afterNavigation();
        viewTitle.setText(getCurrentPageTitle());
    }

    private String getCurrentPageTitle() {
        PageTitle title = getContent().getClass().getAnnotation(PageTitle.class);
        return title == null ? "" : title.value();
    }
}
