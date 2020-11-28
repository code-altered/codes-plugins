/*
 * Copyright (c) 2019-2020, ganom <https://github.com/Ganom>
 * All rights reserved.
 * Licensed under GPL3, see LICENSE for the full scope.
 */
package net.runelite.client.plugins.superheat;

import lombok.Getter;

@Getter
public enum CastType
{
//
//	AUTO_CAST("Auto-cast"), ///remove
//	SINGLE_CAST("Single cast", "Cast"), ///remove
	SUPERHEAT("Superheat", "Cast");


	private final String name;
	private String menuOption = "";

	CastType(String name)
	{
		this.name = name;
	}

	CastType(String name, String menuOption)
	{
		this.name = name;
		this.menuOption = menuOption;
	}
}
