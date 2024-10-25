package com.clan_ban_list_export;

import lombok.Getter;

@Getter
public class BanDetails
{
    private String username = null;
    private final String date;
    private final String bannedBy;
    private final String reason;

    public BanDetails(String username, String date, String bannedBy, String reason)
    {
        this.username = username;
        this.date = date;
        this.bannedBy = bannedBy;
        this.reason = reason;
    }

}
