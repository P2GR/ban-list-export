package com.clan_ban_list_export;

import lombok.Value;

@Value
class HoveredPlayer
{
    String username;
    BanDetails banDetails;
}