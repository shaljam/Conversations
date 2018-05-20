package ir.momensani.tooti;

/**
 * Created by Ali Momen Sani on 5/10/18.
 */
public class PhoneContact {
    private final String phoneNumber;
    private final String name;
    private final boolean isJoined;
    private final boolean isDeleted;

    public PhoneContact(String phoneNumber, String name, boolean isJoined, boolean isDeleted) {
        this.phoneNumber = phoneNumber;
        this.name = name;
        this.isJoined = isJoined;
        this.isDeleted = isDeleted;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getName() {
        return name;
    }

    public boolean isJoined() {
        return isJoined;
    }

    public boolean isDeleted() {
        return isDeleted;
    }
}
