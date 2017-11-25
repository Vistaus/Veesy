package de.veesy.contacts;

import android.content.Context;
import android.support.annotation.NonNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import ezvcard.Ezvcard;
import ezvcard.VCard;
import ezvcard.VCardVersion;
import ezvcard.parameter.TelephoneType;
import ezvcard.property.StructuredName;

import static android.content.Context.MODE_PRIVATE;

/**
 * Created by dfritsch on 18.11.2017.
 * veesy.de
 * hs-augsburg
 * <p>
 * Diese Klasse verwaltet die Visitenkarten.
 */

public class ContactsManager {
    private static ContactsManager unique = null;
    private List<Contact> contacts;

    //TODO In eine Klasse für Konstanten auslagern
    private static final String FOLDER_PATH_APP = "VCards";
    private static final String FOLDER_PATH_CARDS_OWN = "Own_Card";
    private static final String FOLDER_PATH_CARDS_OTHER = "Other_Cards";
    private static final String FILE_ENDING = ".vcf";
    private static final String FILE_NAME_OWN = "Own_Card";
    private static final String FILE_NAME_OTHER = "Other_Card";

    private ContactsManager() {
        updateContactList();
    }

    public static ContactsManager instance() {
        if (unique == null) unique = new ContactsManager();
        return unique;
    }

    //TODO entfernen irgendwann...
    List<Contact> getdummydata() {
        List<Contact> list = new ArrayList<>();
        list.add(new Contact("Fritz", "Markus", "438920", null, null));
        list.add(new Contact("Meier", "Voltin", "458912345", null, null));
        list.add(new Contact("Beutlin", "Angelika", "0987654", null, null));
        list.add(new Contact("Katole", "Johanna", "12345678", null, null));
        return list;
    }

    /**
     * Liest alle Kontakte aus und gibt sie in einer Liste zurück.
     * @return Liste mit allen fremden Kontakten
     */
    public List<Contact> getContacts() {
        return contacts;
    }

    /**
     * Liest die Kontakte neu ein und aktualisiert die Liste der Kontakte.
     */
    //TODO Kontakte aus dem OtherFolder neu einlesen usw...
    public void updateContactList() {
        contacts = new ArrayList<>();
    }

    /**
     * Startet die ViewActivity mit dem angetippten Kontakt, um ihn anzuzeigen.
     * @param context  Context der ContactsActivity
     * @param position Position in der Liste
     */
    void showContact(Context context, int position) {
        context.startActivity(ContactViewActivity.getIntent(context, contacts.get(position),
                false));
    }

    /**
     * Speichert den Konakt als vcf-Datei.
     * @param contact Kontakt, der gespeichert werden soll
     */
    //TODO Bild irgendwie abspeichern
    public void safeContact(Contact contact) throws IOException {
        if (contact == null || contact.getContactPath() == null) {
            return;
        }

        VCard vCard = new VCard(VCardVersion.V4_0);
        StructuredName structuredName = new StructuredName();
        structuredName.setFamily(contact.getNachname());
        structuredName.setGiven(contact.getVorname());
        vCard.setStructuredName(structuredName);
        vCard.setFormattedName(contact.getVorname() + " " + contact.getNachname());
        vCard.addTelephoneNumber(contact.getTelefonnr(), TelephoneType.CELL);
        Ezvcard.write(vCard).go(contact.getContactPath());
    }

    /**
     * Liest die Datei ein und konvertiert sie in ein Kontaktobjekt.
     * @param path Pfad der Datei
     * @return Kontakobjekt mit hinterlegten Daten
     * @throws IOException .
     */
    public Contact readContact(@NonNull File path) throws IOException {
        VCard vCard = Ezvcard.parse(path).first();
        if (vCard == null) {
            throw new IOException("No such Card");
        }

        StructuredName structuredName = vCard.getStructuredName();
        String vorname = structuredName.getGiven();
        String nachname = structuredName.getFamily();
        String telnr = vCard.getTelephoneNumbers().get(0).getText();
        //TODO Richtiges Bild auslesen
        return new Contact(nachname, vorname, telnr, null, path);
    }

    /**
     * Löscht den Kontakt.
     */
    public boolean deleteContact(File file) {
        return false;
    }

    public Contact getOwnContact(Context context) throws IOException {
        File appDir = context.getDir(FOLDER_PATH_APP, MODE_PRIVATE);

        File cardDir = new File(appDir, FOLDER_PATH_CARDS_OWN);
        if (!cardDir.exists()) {
            if (!cardDir.mkdir()) throw new IOException("Can't create new folder");
        }

        String filename = FILE_NAME_OWN + FILE_ENDING;
        File file = new File(cardDir, filename);

        Contact contact = new Contact("", "", "", null, file);
        if (!file.exists()) {
            if (!file.createNewFile()) throw new IOException("Can't create new file");
            return contact;
        }
        try {
            return readContact(file);
        } catch (IOException e) {
            return contact;
        }
    }
}
