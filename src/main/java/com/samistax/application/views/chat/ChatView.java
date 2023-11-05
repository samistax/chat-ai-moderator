package com.samistax.application.views.chat;

import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.samistax.application.service.ChatMessagePersister;
import com.samistax.application.service.ChatMsgConsumer;
import com.samistax.application.service.ChatMsgRepository;
import com.samistax.application.service.OpenAIClient;
import com.samistax.application.views.MainLayout;
import com.samistax.application.views.userlist.Person;
import com.samistax.astra.entity.ChatMsg;
import com.theokanning.openai.completion.chat.ChatCompletionChoice;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.vaadin.collaborationengine.*;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Unit;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Aside;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Header;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.page.Page;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.tabs.Tabs.Orientation;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.shared.Registration;
import com.vaadin.flow.theme.lumo.LumoUtility.AlignItems;
import com.vaadin.flow.theme.lumo.LumoUtility.Background;
import com.vaadin.flow.theme.lumo.LumoUtility.BoxSizing;
import com.vaadin.flow.theme.lumo.LumoUtility.Display;
import com.vaadin.flow.theme.lumo.LumoUtility.Flex;
import com.vaadin.flow.theme.lumo.LumoUtility.FlexDirection;
import com.vaadin.flow.theme.lumo.LumoUtility.JustifyContent;
import com.vaadin.flow.theme.lumo.LumoUtility.Margin;
import com.vaadin.flow.theme.lumo.LumoUtility.Overflow;
import com.vaadin.flow.theme.lumo.LumoUtility.Padding;
import com.vaadin.flow.theme.lumo.LumoUtility.Width;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@PageTitle("Chat")
@Route(value = "chat", layout = MainLayout.class)
//@RouteAlias(value = "", layout = MainLayout.class)
public class ChatView extends HorizontalLayout {

    public static class ChatTab extends Tab {
        private final ChatInfo chatInfo;

        public ChatTab(ChatInfo chatInfo) {
            this.chatInfo = chatInfo;
        }

        public ChatInfo getChatInfo() {
            return chatInfo;
        }
    }

    public static class ChatInfo {
        private String name;
        private int unread;
        private Span unreadBadge;
        private MessageManager mm;

        private ChatInfo(String name, int unread) {
            this.name = name;
            this.unread = unread;
        }

        public MessageManager getMm() {
            return mm;
        }

        public void setMm(MessageManager mm) {
            this.mm = mm;
        }

        public void resetUnread() {
            unread = 0;
            updateBadge();
        }

        public void incrementUnread() {
            unread++;
            updateBadge();
        }

        private void updateBadge() {
            unreadBadge.setText(unread + "");
            unreadBadge.setVisible(unread != 0);
        }

        public void setUnreadBadge(Span unreadBadge) {
            this.unreadBadge = unreadBadge;
            updateBadge();
        }

        public String getCollaborationTopic() {
            return "chat/" + name;
        }
    }

    private ChatInfo[] chats = new ChatInfo[]{new ChatInfo("Moderated - RAG", 0), new ChatInfo("Moderated - finetuned", 0),
            new ChatInfo("Conversation", 0)};
    private ChatInfo currentChat = chats[0];
    private Tabs tabs;

    private ChatMessagePersister messagePersister;
    private ChatMsgConsumer consumer;
    private ChatMsgRepository chatRepository;
    private final OpenAIClient aiClient;

    public ChatView(ChatMessagePersister messagePersister, ChatMsgConsumer consumer, ChatMsgRepository chatRepository, OpenAIClient aiClient) {

        this.messagePersister = messagePersister;
        this.consumer = consumer;
        this.chatRepository = chatRepository;
        this.aiClient = aiClient;

        addClassNames("chat-view", Width.FULL, Display.FLEX, Flex.AUTO);
        setSpacing(false);

        // UserInfo is used by Collaboration Engine and is used to share details
        // of users to each other to able collaboration. Replace this with
        // information about the actual user that is logged, providing a user
        // identifier, and the user's real name. You can also provide the users
        // avatar by passing an url to the image as a third parameter, or by
        // configuring an `ImageProvider` to `avatarGroup`.
        UserInfo userInfo = null;
        // Create user info from currently selected user
        Person userListPerson = VaadinSession.getCurrent().getAttribute(Person.class);
        if ( userListPerson != null ) {
            userInfo = new UserInfo(userListPerson.getId().toString(), userListPerson.getName());
            userInfo.setColorIndex(userListPerson.getColorIndex());
            userInfo.setImage(userListPerson.getImage());
        } else {
            System.out.println("No user selected from list" );
            userInfo = new UserInfo("0", "AI Moderator");
        }
        tabs = new Tabs();
        for (ChatInfo chat : chats) {
            // Listen for new messages in each chat so we can update the
            // "unread" count
            MessageManager mm = new MessageManager(this, userInfo, chat.getCollaborationTopic());
            mm.setMessageHandler(context -> {
                if (currentChat != chat) {
                    chat.incrementUnread();
                }
            });
            chat.setMm(mm);
            tabs.add(createTab(chat));
        }
        tabs.setOrientation(Orientation.VERTICAL);
        tabs.addClassNames(Flex.GROW, Flex.SHRINK, Overflow.HIDDEN);

        // CollaborationMessageList displays messages that are in a
        // Collaboration Engine topic. You should give in the user details of
        // the current user using the component, and a topic Id. Topic id can be
        // any freeform string. In this template, we have used the format
        // "chat/#general".
        CollaborationMessageList list = new CollaborationMessageList(
                userInfo,
                currentChat.getCollaborationTopic(),
                messagePersister);
        list.setSizeFull();

        final UserInfo finalUserInfo = userInfo;
        list.setMessageConfigurator((message, user) -> {
            if (user.getId().equals("0")) {
                message.addThemeNames("ai-user");
            } else if (user.getId().equals(finalUserInfo.getId())) {
                message.addThemeNames("current-user");
            } else {
                message.addThemeNames("other-user");
            }
        });
        // `CollaborationMessageInput is a textfield and button, to be able to
        // submit new messages. To avoid having to set the same info into both
        // the message list and message input, the input takes in the list as an
        // constructor argument to get the information from there.
        CollaborationMessageInput input = new CollaborationMessageInput(list);
        input.setWidthFull();

        // Give ChatMsgConsumer a UI component handle to be able to publish messages ( received from Pulsar)
        this.consumer.setChatView(this);

        // Change the topic id of the chat when a new tab is selected
        tabs.addSelectedChangeListener(event -> {
            currentChat = ((ChatTab) event.getSelectedTab()).getChatInfo();
            currentChat.resetUnread();
            list.setTopic(currentChat.getCollaborationTopic());
        });
        // Add listener to catch new chat messages
        TextField msgField = new TextField();
        msgField.setPlaceholder("Message");

        Button button = new Button("Send", VaadinIcon.PAPERPLANE.create());
        button.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        msgField.addValueChangeListener(vce ->
            System.out.println("Message value changed: " + vce.getValue())
        );
        list.setSubmitter(activationContext -> {
            button.setEnabled(true);
            Registration registration = button.addClickListener(
                    event -> {
                        String message = msgField.getValue();
                        if ( message.length() > 0 ) {

                            activationContext.appendMessage(message);
                            String topic = currentChat.getCollaborationTopic();

                            System.out.println("finalUserInfo: " + finalUserInfo.getName());
                            System.out.println("CurrentChat topic: " + topic);
                            // If not moderation chat channels, then we are using conversational chat channel and need to retrieve AI response
                            if (!topic.contains("Moderated")) {
                                String promptResponse = callChatCompletion(currentChat.getCollaborationTopic(), finalUserInfo.getId(), message, 10);
                                UserInfo botUser = new UserInfo("0", "system");
                                CollaborationMessage botMessage = new CollaborationMessage(botUser, promptResponse, Instant.now());
                                currentChat.getMm().submit(botMessage);
                                msgField.clear();
                            }
                        }
                    }
            );

            return () -> {
                registration.remove();
                button.setEnabled(false);
            };
        });



        // Layouting
        VerticalLayout chatContainer = new VerticalLayout();
        chatContainer.addClassNames(Flex.AUTO, Overflow.HIDDEN);

        Aside side = new Aside();
        side.addClassNames(Display.FLEX, FlexDirection.COLUMN, Flex.GROW_NONE, Flex.SHRINK_NONE, Background.CONTRAST_5);
        side.setWidth("18rem");
        Header header = new Header();
        header.addClassNames(Display.FLEX, FlexDirection.ROW, Width.FULL, AlignItems.CENTER, Padding.MEDIUM,
                BoxSizing.BORDER);
        H3 channels = new H3("Channels");
        channels.addClassNames(Flex.GROW, Margin.NONE);
        CollaborationAvatarGroup avatarGroup = new CollaborationAvatarGroup(userInfo, "chat");
        avatarGroup.setMaxItemsVisible(4);
        avatarGroup.addClassNames(Width.AUTO);

        header.add(channels, avatarGroup);

        side.add(header, tabs);

        HorizontalLayout inputLayout = new HorizontalLayout(msgField, button);
        msgField.setWidth(80, Unit.PERCENTAGE);
        button.setWidth(20, Unit.PERCENTAGE);
        inputLayout.setClassName("messageInput");
        msgField.setClassName("messageInput");
        button.setClassName("messageInput");
        inputLayout.setWidthFull();

        chatContainer.add(list, inputLayout);
        add(chatContainer, side);
        setSizeFull();
        expand(list);
    }

    private ChatTab createTab(ChatInfo chat) {
        ChatTab tab = new ChatTab(chat);
        tab.addClassNames(JustifyContent.BETWEEN);

        Span badge = new Span();
        chat.setUnreadBadge(badge);
        badge.getElement().getThemeList().add("badge small contrast");
        tab.add(new Span("#" + chat.name), badge);

        return tab;
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        Page page = attachEvent.getUI().getPage();
        page.retrieveExtendedClientDetails(details -> {
            setMobile(details.getWindowInnerWidth() < 740);
        });
        page.addBrowserWindowResizeListener(e -> {
            setMobile(e.getWidth() < 740);
        });
    }

    private void setMobile(boolean mobile) {
        tabs.setOrientation(mobile ? Orientation.HORIZONTAL : Orientation.VERTICAL);
    }
    private String callChatCompletion(String topicId, String userId, String prompt, int memorySize) {
        String response = "";


        List<ChatMessage> messages = new ArrayList<>();
        // Retrieve last messages from database ( fetch memory items for LLM)
        //List<ChatMsg> messageHistory = chatRepository.findByKeyTopic(topicId);
        List<ChatMsg> messageHistory = chatRepository.findByKeyTopicAndUserId(topicId, userId);

        // Start by giving prompt instructions
        messages.add(new ChatMessage("system", "You are a nice chatbot having a conversation with a human. Use chatHistory function to access past conversation or if the user asks something personal."));

        messageHistory.forEach(row -> {
            messages.add(new ChatMessage("user",row.getText(), row.getUserId()));
        });

        // add user input as the last message
        ChatMessage userPrompt = new ChatMessage("user", prompt);
        userPrompt.setName(userId);
        messages.add(userPrompt);

        try {
            // Build Chat Completion Request
            ChatCompletionRequest completionRequest = ChatCompletionRequest.builder()
                    .messages(messages)
                    .model("gpt-3.5-turbo")
                    .maxTokens(256)
                    .build();
            List<ChatCompletionChoice> completionChoices = aiClient.createChatCompletion(completionRequest).getChoices();
            response = completionChoices.get(0).getMessage().getContent();

            System.out.println("callChatCompletion: model used ->  " + completionRequest.getModel());
            System.out.println("callChatCompletion: user msg ->  " + messages.toString());

        } catch (Exception ex) {
            System.out.println("Exception: " + ex);
            response = ex.getMessage();
        }
        return response;
    }
}
