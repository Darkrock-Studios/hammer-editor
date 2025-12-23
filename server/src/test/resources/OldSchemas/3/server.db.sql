BEGIN
TRANSACTION;
PRAGMA
user_version = 3;
CREATE TABLE IF NOT EXISTS "account"
(
	"id"
	INTEGER
	NOT
	NULL,
	"email"
	TEXT
	NOT
	NULL
	UNIQUE,
	"salt"
	TEXT
	NOT
	NULL,
	"password_hash"
	TEXT
	NOT
	NULL,
	"cipher_secret"
	TEXT
	NOT
	NULL,
	"created"
	TEXT
	NOT
	NULL
	DEFAULT (
	datetime
(
	'now'
)),
	"is_admin" INTEGER NOT NULL DEFAULT FALSE,
	"last_sync" TEXT NOT NULL DEFAULT
(
	datetime
(
	'now'
)),
	PRIMARY KEY
(
	"id"
	AUTOINCREMENT
)
	);
CREATE TABLE IF NOT EXISTS "auth_token"
(
	"user_id"
	INTEGER
	NOT
	NULL,
	"install_id"
	TEXT
	NOT
	NULL
	UNIQUE,
	"token"
	TEXT
	NOT
	NULL,
	"refresh"
	TEXT
	NOT
	NULL,
	"created"
	TEXT
	NOT
	NULL
	DEFAULT (
	datetime
(
	'now'
)),
	"expires" TEXT NOT NULL,
	PRIMARY KEY
(
	"token"
),
	FOREIGN KEY
(
	"user_id"
) REFERENCES "account"
(
	"id"
) ON DELETE CASCADE
	);
CREATE TABLE IF NOT EXISTS "deleted_entity"
(
	"user_id"
	INTEGER
	NOT
	NULL,
	"project_id"
	INTEGER
	NOT
	NULL,
	"entity_id"
	INTEGER
	NOT
	NULL,
	"deleted_at"
	TEXT
	NOT
	NULL
	DEFAULT (
	datetime
(
	'now'
)),
	UNIQUE
(
	"user_id",
	"project_id",
	"entity_id"
),
	FOREIGN KEY
(
	"project_id"
) REFERENCES "project"
(
	"id"
) ON DELETE CASCADE
	);
CREATE TABLE IF NOT EXISTS "deleted_project"
(
	"user_id"
	INTEGER
	NOT
	NULL,
	"uuid"
	TEXT
	NOT
	NULL,
	FOREIGN
	KEY
(
	"user_id"
) REFERENCES "account"
(
	"id"
) ON DELETE CASCADE
	);
CREATE TABLE IF NOT EXISTS "project"
(
	"id"
	INTEGER
	NOT
	NULL,
	"uuid"
	TEXT
	NOT
	NULL,
	"user_id"
	INTEGER
	NOT
	NULL,
	"name"
	TEXT
	NOT
	NULL,
	"last_id"
	INTEGER
	NOT
	NULL
	DEFAULT
	0,
	"last_sync"
	TEXT
	NOT
	NULL
	DEFAULT (
	datetime
(
	'now'
)),
	PRIMARY KEY
(
	"id"
	AUTOINCREMENT
),
	UNIQUE
(
	"name",
	"user_id"
),
	FOREIGN KEY
(
	"user_id"
) REFERENCES "account"
(
	"id"
)
	);
CREATE TABLE IF NOT EXISTS "server_config"
(
	"key"
	TEXT
	NOT
	NULL,
	"value"
	TEXT
	NOT
	NULL,
	"updated_at"
	INTEGER
	NOT
	NULL
	DEFAULT (
	strftime
(
	'%s',
	'now'
)),
	PRIMARY KEY
(
	"key"
)
	);
CREATE TABLE IF NOT EXISTS "story_entity"
(
	"user_id"
	INTEGER
	NOT
	NULL,
	"project_id"
	INTEGER
	NOT
	NULL,
	"id"
	INTEGER
	NOT
	NULL,
	"type"
	TEXT
	NOT
	NULL,
	"content"
	TEXT
	NOT
	NULL,
	"hash"
	TEXT
	NOT
	NULL,
	"cipher"
	TEXT,
	UNIQUE
(
	"user_id",
	"project_id",
	"id"
),
	FOREIGN KEY
(
	"project_id"
) REFERENCES "project"
(
	"id"
) ON DELETE CASCADE,
	FOREIGN KEY
(
	"user_id"
) REFERENCES "account"
(
	"id"
)
	);
CREATE TABLE IF NOT EXISTS "white_list"
(
	"email"
	TEXT
	NOT
	NULL,
	PRIMARY
	KEY
(
	"email"
)
	);
INSERT INTO "account"
VALUES (1, 'test@test.com', 'bcVKrNNT', 'a7f67850b2acac3d0944d5d4eb19a13c3bd1baeb2452f01cad0f09796561b899',
		'q0DbacacEUjx0HtOLsjs5w', '2024-10-25 05:12:08', 1, '+100002-12-31 23:59:59');
INSERT INTO "account"
VALUES (2, 'test2@test.com', 'bcVacacT', 'a7f67850b2acac3d0944d5d4eb19a13cxxxxbaeb2452f01cad0f09796561b899',
		'q0Dbacacxxxx0HtOLsjs5w', '2024-10-25 06:12:08', 0, '+100002-12-31 23:45:59');
INSERT INTO "auth_token"
VALUES (1, 'bc1e67da-13a0-41be-8b15-7e1240441b02', '91qiSZnTrk-QiTKnYzhX-Q', 'Jt3BOq0X4h_kc9sD6Z2nKw',
		'2025-03-31 02:40:52', '2025-04-30T02:40:52.144776200Z');
INSERT INTO "auth_token"
VALUES (1, 'c3b347e6-9489-4cc1-bf75-47ee13df8edd', 'E3U8gsjhqwI1uN70y7pS2w', 'MgMnjo1RRBLI4wZk5_N8CQ',
		'2025-03-31 04:25:53', '2025-04-30T04:25:53.369370900Z');
INSERT INTO "auth_token"
VALUES (1, '06ecf0be-c966-492e-a3d9-066397ec7324', 'ql76mHSBKPKuPMSJof_pVg', 'VC4xE8XFlij1tiAIklOX5g',
		'2025-12-17 22:07:23', '2026-01-16T22:07:23.675739100Z');
INSERT INTO "auth_token"
VALUES (1, 'web', 'Bnc-vg7SsdY2X116gieU0A', 'hsJA3AGaS7ha2dfkg4dFGg', '2025-12-17 22:08:49',
		'2026-01-16T22:08:49.215159100Z');
INSERT INTO "auth_token"
VALUES (1, 'a6de8ce5-7dfa-43df-96d9-40945520b237', '8yMtsZuoTavIw5tQhn78Bw', 'CYZVOTBbC3z58b2M770Tkw',
		'2025-12-18 07:09:17', '2026-01-17T07:09:17.284421800Z');
INSERT INTO "deleted_entity"
VALUES (1, 4, 54, '2024-10-25 05:12:11');
INSERT INTO "deleted_entity"
VALUES (1, 4, 109, '2024-10-25 05:12:11');
INSERT INTO "deleted_entity"
VALUES (1, 6, 19, '2025-12-19 00:54:25');
INSERT INTO "deleted_project"
VALUES (1, '463f4b74-f6a2-49cd-9226-73190071da26');
INSERT INTO "project"
VALUES (2, 'd06885de-bfd6-48e7-b461-be1d94f3ab0d', 1, 'Apophis', 8, '2025-12-21 08:43:57');
INSERT INTO "project"
VALUES (3, '99e15773-27c1-4056-81d3-4b7bf377fefb', 1, 'Incongruous Events', 4, '2025-12-21 08:43:57');
INSERT INTO "project"
VALUES (4, 'c8691d80-d153-44a1-9282-8a9f2adfaa07', 1, 'Insurgency', 110, '2025-03-31 04:26:16');
INSERT INTO "project"
VALUES (5, '44ec768d-68a4-4e22-ae51-53c970dbcc9b', 1, 'The Dig', 16, '2025-12-21 08:43:57');
INSERT INTO "project"
VALUES (6, '4d47f6de-dbdb-4929-bb3f-0ad3d9a24a26', 1, 'Alice In Wonderland', 50, '2025-12-21 08:43:57');
INSERT INTO "server_config"
VALUES ('whitelist_enabled', 'true', 1766211538);
INSERT INTO "server_config"
VALUES ('contact_email', 'test@test2.csom', 1766213596);
INSERT INTO "server_config"
VALUES ('server_message', 'Welcome to this instance of Hammer! Happy syncing!', 1766213596);
INSERT INTO "server_config"
VALUES ('default_locale', 'de', 1766213596);
INSERT INTO "story_entity"
VALUES (1, 5, 1, 'scene',
		'v6GN/Lbs5RVGjw2exuID9KyRVRiTCF2qq0N5OfXC4j2VvK7Q7FvJJq+IPta5fhiakwV1UB+RqLzANsyrXPEEKy4qxEfEv1FDsLvfPfiK8fRnzga7zPgy+dis0uCtM3AaDi6DzNrfap9QD2kKaQBlzfS03kVmFcEJUl/FOIWEENFfbOoRUj+KmZTNsUCLJ+ZrAnXERkxNiK2pAnHjnwhd1y3BhTZHVrFCL5UsS+7Do6/Yy29aHtxekkAzfY2CEAOBBDpbgRq+QUpWQrfQfmfjPZ4TOvp3poPSK4ZulA2tkGb8j6sQyQD//CUyQLcVd7zyiuXvsMkdlHMcIOp78I4tgjIt2EE3/81FS3fxL3LrChLV/ZVJ5NVscNucvsH4P5xEbp4K9R9ACHA6GYSOVCqDRzT2ex2xzeBfiGYAGHuTTaPGYQcM6uuP2Tq/J01zk1nsrjjL8zzcz1CnAhGQq7SAhlhro0Pcj8j1395usjWkxoydz8oUyUPo52V1KzNxedB1rEPfsKmLCJkq/RD2bPorJz8cSUnFOs06e6kqAymOK7lcBIoDYL9UuaWovX8Wn3Wzx3/pCc5cj00MXdPz1ZmCt9+Rm/0w7A3+vDsGQpUe9doka6Vu1O6aN5h0q83uh4KWCfS8gkx71XCE7P9epYICOalq7UFvG2rc2ta2+tJgdtJgNOInDKr8aoX50F8m8cJJqcM7eJro1zvSDpZGcRX4iZlekUC4tyEjyR1qEZNVSbEe/48pGz/o0OMJ+5I5kgQQAEwzxcqCvLfAz+M74LaIzB0FOG+YIrTDAb6/KvgGWq4dYNG7iRdxvDYsRIle5EztfJOeZ1B++TVURJ/M6/jQsvb0Qsiq0hgGXc9tUumfUwRbfsYl6jvjUdmXBuZXD387RcC08RjmEtNo8q3QCt0Y2jexEB1JEV4cqa63ymsgLcu8peNyRwVjScYlxhgPZ1zdtu78FDnBTic4fuT0DopmjG9iL6UsSxm4mFE+wN4MjIhVPmG+BwRCYMpoHnnwYNcsSaPnjGg/vA03dc+xPlRD81gaoRVdbiuEjlA4p1hxhSvZNv5+BSJot7ui6X+zEBansRjhH5Xv5MDYiUchV90xL5byHZXWYWsFXaqpgCrLgtvqb3Znb+q0RxKQG6E15nDsi5E2A6r3DywjU5MpzPIO2HwDN8V7ZuvdcUy3SsafDOwcuWWaKcTb6SDlfxLjYNNt46Q+mrl5LUzz5mkoDJXTzB04VNeJSXN93t9zwsDkKepfbTZW3eHmUKBRqKyDVYe+uJyCuF6+GBcmcd1naDjWDIfwgAH11Zm4TgJ45PV+MY1lSHyEgzRu/sCN9u1w9xOl+7KUO1QF3+/vupiuTKoiGga09hGe9eVq3fOCfvocBI33cimHakMQ9qtlgxeHd6yWPx4lJPBShDOlCIPWclbDip2hxa88qLdq7uLfdzDl/aing3EzAHTc22JnxQgl9k5YcG3A/ydPQHfEqWJ4cBAfYPKi4fjCNrl2neno+LmCbL8Lzo/DLwnzoBu3nqA9jDhv/pwTSdobiXB5PnGkX5JLaUNwEyaQRd8SFNM82QOZk/jUw4SatM2lId9gm3uonuqs4e1L6W5c1YDQC3oKJoLTWcveYWMSmE+7mbAevTOHUI6+pvc4fVnEd14AIQEoHfzc44PCrqJQGYJwXt3od4XIq6VMOBhBVBd6B4vJ8Mr7ntuL70NjHJS9VyWqfv817RpU4iphbLryD2twL29qwn1poBO6lg7CKLHhqKN8B0uZu/kNRhl54axr79h9IHgaIGIp7R87UJKQgKFsNsF0UztZlV5+DNOEgQgaiRxLmcjmK6PdTwXkJw1pFNzi86+6wrLAyUM9kVQXJ2xYWFZWpma4NdL9QmNUXQnDT2igZTbNydZWoUebpUCoO4X5mgbStpSwH5WeHo4c/mF59cs6h8jyvM/RKTqBipiJaYFyYQwCc40AWm7mO5Mio3aUVsNBzYISmSMquTtafU6RWVtdvO9vzx+Sbn4g9WkPeSQcSlpODIXsMnUtGYC2RASj1fyJxGU0sOMJD3ub1XsuW5K9wR/0wWafhYFSLdL/faaabpapYdUkA2vCY1o9aa1Cm7UF/+l1plJ3BZHQRa1zqWPkPcmYmmpsfjblKAyDyG/TzkcIxE3EUob8ImueglJAGC7UUYXrLSThKiYrMWWoZf+TiPI2ugtwHaGY2F4139BX07q/ftbiRuQ=',
		'jzcrk54mffqEe11hcLbXXw', 'AES/GCM/NoPadding');
INSERT INTO "story_entity"
VALUES (1, 6, 39, 'timeline_event',
		'/uPXIrqdsMIyHmv/TRd9ng8tOLhV7esIJ8x5PZtEAGJ2yCo8E43tyI74hebXuAW7MThdrp0pT0Y/rp/h+fYZ93JNKnjrE53nAqXgnH+mFrQggJcGKS70f3RP8IFbbswFuBkq0nA6ZTizQ4lWkk0faKKmPo+zAo8pn2f+/6p7u6TQr3Rd6QKlbX6WgJpr1SeGo444rO29nfcSE783oL8hN0PKmntYa/ZzU7hkBFpnI37IeVD1R8R4mrNUMNIZNZe3q4LiIHbWi+uI3cc0t4EF277NZJ5/YVdFrRkQGVAwkSlR5aCdp6codS4=',
		'nx9I6AppZxY0nlPTmKTwRw', 'AES/GCM/NoPadding');
INSERT INTO "story_entity"
VALUES (1, 6, 40, 'timeline_event',
		'16LKI3gH38+qRRk4fkGP8l9zy+aTiImXmcSw+thH3E65AW5gPcc3hPCnK9HsVCvVgZWULefANxYig0/NH3ZsQaf0TN/ZhCekQyOPSYxioBLYpSBWphblEEJEbYhEYH9f3TNWEdBcfbIiX+4rLnY4GwdAMf2KpsyeM4YW8y0Y2Usi46fL+n6DdAVezUYOxzzVwpUnZlR70dmU5ZUr9ATDdg44ObZcHVHrzNhbVb5xfZxuYO1itIesAhKPKpgdjlgjvFQvLJ5s7rXT9gfiFfKRhrbZKoGNTFiIThqs4Lw89VZ978D05FrjZoYnDZ6OtVsKLRfDufQhhl80N54WZFQtU8Qbc8jzpJlwmrM2Q9452NlWyC62MOk31cyq/nWjqQ5txxpH9DecVjP9ekfrQu7x+bFypqHDSqN/JTONSGHnKWdEwDeSd1IS8hReDZhLwtbKF69ge0NwpPY=',
		'afinb1kXWSh6AQlr2n1hGw', 'AES/GCM/NoPadding');
INSERT INTO "story_entity"
VALUES (1, 6, 41, 'timeline_event',
		's7hfhga1pRh5VUZYlYzBxCNk1ZyamUG9kIfWq2XL7ZQUkwcUAW4XfxjQ0pJdk6J2Xt188J1SgFHk6qI1bEldk5TP/uMp2kcEAsV6oE9+Eha998e1zvD0o06LRCc6AV/MHcBHX1Xt0/mrwBaCziR8xcHJax+nhfA3w/8jpmZFiSmlHiRYOSYjTAZbgdx/iF7Twnk/OOzSNcS5W32mhsN/lrXRi0zJ3MYA7AizIokitoWuHU6th5pw+hkXPQNE96VVgo/EDAbHWFuR3qzfoPVUpPeMECN/aOcNeljwL47YiRVdgbB7gDL5g+XYOS/dk7s5R/dLCOplvdYjAc5s3wggK/NARDPgoprdZWAeW9Moh/QumBY0pr6afkxr2h2xeCn3FdqaaIDFJCpoRYcz/Q49xjswfCQb6LcDgGnV',
		'AFut-V1eDku75CycRZU_yA', 'AES/GCM/NoPadding');
INSERT INTO "story_entity"
VALUES (1, 6, 42, 'timeline_event',
		'ApRGfTd8DNPgDyHgwFnikpKZzfjnfL5JfRZNDDmnrOdfCKz/LyIdCZDd1SZJErNlNjbWQMvQ3PLN6lE1H0A3z4NArn9zmctBijaj/kLim/76jdB6++z/dX/SCtghR6rrmsvWvJbxi43ZwpJCgMiBegWF4TbZOZBo1pjBMDONIb1ZiWte82yl16IOZ25EOlnZ2XbwNkowa9O5dfjhcsW30as4G45NukhEMIYodwHOqtGYQXkNXzIUG3R0GzWF',
		'LsONiuvFrM1dJkIpRHMpbw', 'AES/GCM/NoPadding');
INSERT INTO "story_entity"
VALUES (1, 6, 43, 'timeline_event',
		'VboI5OHKJUCATVl4wCdRVD3Dj0HDJfydz5tU5NQ+S7UZmTLA9k58JoseBfik9wCUAlWOm31eQWubKt+dqfAIH3pl4zyhAoQfal/dkWRt4RW9zPbkLsbNnj5CQqfXDgynAchKcBNOz81n9jNr2PUFqzrp2eOdGn7NuLYkJ/kH88Slxjmvh9aYgxK/ASz8WTsLoqNwAzZDhVi5A1RuoqW4jNCxZSwT8BySMhcV5e7q9NqjKQYpe9LqYhSk/QgcFdLO6dW8XxCrVqN7uet+7mXMSRX0SymFhM7stXLUsjiBoFFJheFn100zZhqZ9FqO',
		'5SYTL_7h_Z9tUz_OwNbTgw', 'AES/GCM/NoPadding');
INSERT INTO "story_entity"
VALUES (1, 6, 44, 'timeline_event',
		'eTRI9k4aIEpNbPjqByxo0y0bpG9BEh8M2Z5OlKHMRjnUWyUyQEJqUmJzn7YTaAWqoJtSZRECIjs4rwVpBAe5I1W+ZA8g+RxGrx+zNC+8CC3UTbMkDiKmW/ffXMeIDYnWFJlTfkH6B6qv2gkYo9Kw/j+Sx48Rtxmyqwh0n9YdcDWQdQj5WbmO4Kyi9fiPNVJO6dOxZRzuGNJABk2XWQ9iNVwhcluSDyASpC7+8+Zwpfw8jDwWAY/xTeMiMHM0llHJnXVo/SkfCOt9by9uMlXMm3MUr+NKOcsxbwm9ySsceJWebb0DN3S9XV2g3fMhGckfAUuL9apvhGLlKoLHyWJd6f0qe/HShMOzfmAyqNipdmAXBt1dl7aRDe2AlA2Wvrwsq106DYfZAENNHlCnpBGe9htV68jz92bcNpfeXvo=',
		'8xgQJVNbrUXZJ5G-F89lmA', 'AES/GCM/NoPadding');
INSERT INTO "story_entity"
VALUES (1, 6, 45, 'timeline_event',
		'7NrlhbYnwzTtB03GxsPdrnH11Y67pQHUJstAHUCu9kDVsPUtHkkzp/TcMF2tcFr6SdVGqEteuewmYgL3eVC+ZDH7fQLTK5G6WAK9ahhXvpWnvN3fqgIvPH50tvqlAccd09W0nH5fJxISh/rY4Wad7USvdya7UDZiZpnptx1pYOdjAak+w4RYfJ4lQGMhuBTwXOAKPkwMd0VYN4OgUQzR2DoQLf+zgO/P5u/nYBJyv5S/OowEmQw944WPqAucUpX94jTKuG5FZWQuqNO7FEMy',
		'Nsh3MM2QDREdqk-5b33rgw', 'AES/GCM/NoPadding');
INSERT INTO "story_entity"
VALUES (1, 6, 46, 'timeline_event',
		'fDhPmBRCtwboZjcl0NaSl2brnAMv5v1arkFZ7NbRYGf49K+m/gcy0a/k82B+a8O+BEnfFef8yrJQqQTm8q3pIkAkhbCsDF3AO5j03BipXPYVR0EjjfUNi7MrUwM4TJ+iWHyscqoOrK/KosNa0cTS6hKM2ng7eb03c6ilekA7JfJZfUEhHkttvvoN8jsqGzkUTu9RlQ0hLi9bSOnblDvaMxzpmcUX4nsQDql4lsMPC/qfHYdaosgdYQyGbMYMVr/GpELvcyStPdllRm1HWWfrEk5t/eed',
		'8zmSbTIxZjNNnR19DnU6HA', 'AES/GCM/NoPadding');
INSERT INTO "story_entity"
VALUES (1, 6, 47, 'timeline_event',
		'ZvZBj2YyteSjLUevsp7dv+k/FWh9ESGe0MdQ4Ha4SB2NQsTRB9tHT+uDeiJtsY96vzK5DtAN9DAeCdnOHnq2Vs9faLXFFgf/P9YSitW2aI14PzNzjSVqBaxdeiWA7i0mSDx85WrSYJrF8rvn30QAH2SINeiWDMNkX9jxL45vqnfib65LKGTsfZwnIB9PLU/L8IQd5htqQo4PRKqUtCT5KWC177YO/iPMpdtrbDDX24w7RnwIe4nmWctfon12FcQndyAmveezYKuCkEvKlLPjJiCdwrEiXDnYt65Ocr0eks2t9QdT/4t/qrOcktTraHELa34Sw29zH4KdAAYDplFzKEvHXNOJfA==',
		'qyvrltg2XKldgEy5A9n36A', 'AES/GCM/NoPadding');
INSERT INTO "story_entity"
VALUES (1, 6, 48, 'timeline_event',
		'zE/omiSzpraSSKUBNAvVov+o//9XojSn1iBOsPRxQPmZBbBzhJ4f4Ha7rINgoa5ZSwpzNB30z9rMlA+WjOZehSm7Svz73zNP2yjiKKW8lxAsYuB6oWk0Q1HR6bH7gmwHHd73/t7EOJgCjtCbpNSel6qOqrGyElAzuuvz08V5psGIIJxuU661Qe8Updpf/6qKH+EPubrYs1SZfrXDzUAPBXktmzfUnHJXlBfKraTIFaF8mMY7Vh1odQdzVgZC5CtltQ3M/qkxViKyxMbQSHfQHHnxu1RZgrBcb3TqqlA=',
		'dZrcfU-4RVCO8MkUdFHf5g', 'AES/GCM/NoPadding');
INSERT INTO "story_entity"
VALUES (1, 6, 49, 'timeline_event',
		'5ulE2PAGT3EsCGvR+8gSVc2jnKr+457OQ8FaLx1kOEy2r6tlEERifAv+lSNM7CXH0p1QNhLZrjFpIcXbhHYXM7FvaxfbN/KRVOJ91sJ1DmhuiVwLj1dNyKsNOBiMKOfacC2qnXOD93xgbddMRmz1MGC9NmDgbmzN3azXoRcwqzF0OFexREOGUP69S2zQxqXzV5KVBwZGFrF9fDN2vTANnb2IdeCzFqI8x9Q+bOC/61zcVxHXhB9/rwUgJWqPL2duKELcxS0LwGIjdSroa0EhtJlR7B6AzZXR+3rK1afAx2ZrIL9F1gMy1X//c7+gUNz2dvHWGO+ivDQuXdnPhrRGccqLSAYxXjVSCpu/',
		'gcfTrOXLzib03LB88UfIIA', 'AES/GCM/NoPadding');
INSERT INTO "story_entity"
VALUES (1, 6, 50, 'timeline_event',
		'nJ0NRLNEkBHTOC2bB7mLCGcceSFJdY8EzmboE3D+xWTW1I6+aClEzO8cDFpuDmqEW3gfxh7JsN8RRyUCXgm/WP1coi7sY6fIZSmS8aRw0QYPA5nlNkM+HCudrcIE8loyU63/4I1w+a0kAhtkWSknZbH1AgFg3oSjFJxym+hhlq7Yxj+jOO0/Pb9cz2m4wFsfFROxmf7oQ/1nM0orntTRfpbsh7I9HzVqUT8POGF4QlEMfqaQKU5z0zAZ8WM+cqyjwWJtgIlNj49De4ROAQzBsTrGKRkE5uEbTrPhp6WWK/ynf6osP4BZsOA/GlKThX5g',
		'ewXB6C7EHh7DuEr4K-GGEw', 'AES/GCM/NoPadding');
INSERT INTO "white_list"
VALUES ('tester2@test.com');
INSERT INTO "white_list"
VALUES ('tt@ee.com');
INSERT INTO "white_list"
VALUES ('asdwq@zxc.com');
INSERT INTO "white_list"
VALUES ('asd@dfg.com');
INSERT INTO "white_list"
VALUES ('ttt@eee.com');
INSERT INTO "white_list"
VALUES ('vv@ll.com');
INSERT INTO "white_list"
VALUES ('qqww@dd.com');
INSERT INTO "white_list"
VALUES ('asd@tt.com');
CREATE UNIQUE INDEX IF NOT EXISTS "deleted_entity_lookup" ON "deleted_entity" (
	"user_id",
	"project_id",
	"entity_id"
	);
CREATE INDEX IF NOT EXISTS "deleted_entity_lookup_all" ON "deleted_entity" (
	"user_id",
	"project_id"
	);
CREATE INDEX IF NOT EXISTS "entity_def_by_type" ON "story_entity" (
	"user_id",
	"project_id",
	"type"
	);
CREATE UNIQUE INDEX IF NOT EXISTS "user_project_name_lookup" ON "project" (
	"user_id",
	"name"
	);
COMMIT;
