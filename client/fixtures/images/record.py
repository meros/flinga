"""Adds the F16 image and detail fields to the recorded fixtures, and records a few scaled images.

    python3 -I client/fixtures/images/record.py <path to a copy of the app's shared_prefs/api.xml>

Read-only: GraphQL queries (no mutations) and image GETs; it never touches the login or token endpoints.
It merges only the new fields into the existing fixture files, so the recorded playability, progress and
ids stay as they were (the tours depend on them). Images are fetched as the official app builds the URL
(ImageProxyImageUrlTransformation): convert/?resize=WxH&shape=nocut&source=<source>, with Accept: image/jpeg.
"""
import html
import json
import os
import re
import sys
import urllib.parse
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
FIX = os.path.dirname(HERE)
PROXY = "https://director-image.streaming.telia.com/convert/"
HERO, THUMB, ROW = (960, 540), (176, 100), (192, 108)
# Episode thumbnails for these seasons only (Bonde söker fru S21 and S20, Storage Wars S13).
THUMB_SEASONS = {"fdfdbd40834e0440", "568fe512daec2363", "f2fb9d88c92b124f"}

prefs = dict(re.findall(r'name="(\w+)">([^<]*)<', open(sys.argv[1]).read()))
TOKEN = html.unescape(prefs["accessToken"])


def gql(query, variables):
    body = json.dumps({"query": query, "variables": variables}).encode()
    req = urllib.request.Request("https://graphql-telia.t6a.net/graphql/", data=body, method="POST", headers={
        "Content-Type": "application/json", "authorization": "Bearer " + TOKEN, "x-country": "SE",
        "tv-client-name": "androidtv", "tv-client-version": "9.23.2", "tv-client-boot-id": "b-fixtures",
        "tv-client-os-name": "Android"})
    if query.lstrip().startswith("mutation"):
        raise SystemExit("read-only")
    return json.loads(urllib.request.urlopen(req, timeout=20).read().decode())["data"]


def scaled(source, size):
    """The same URL as Images.scaled in the app."""
    enc = source if "%2F%2F" in source[:16] else urllib.parse.quote(source, safe="")
    return "%s?resize=%dx%d&shape=nocut&source=%s" % (PROXY, size[0], size[1], enc)


index_file = os.path.join(HERE, "index.json")
index = json.load(open(index_file)) if os.path.exists(index_file) else {}


def image(source, size, name):
    if not source:
        return
    url = scaled(source, size)
    if url in index:
        return
    r = urllib.request.urlopen(urllib.request.Request(url, headers={"Accept": "image/jpeg"}), timeout=20)
    data = r.read()
    fname = "%s-%dx%d.jpg" % (name, size[0], size[1])
    open(os.path.join(HERE, fname), "wb").write(data)
    index[url] = fname
    print("image", fname, len(data), "B")


def load(name):
    return json.load(open(os.path.join(FIX, name)))


def save(name, doc):
    with open(os.path.join(FIX, name), "w") as f:
        json.dump(doc, f, ensure_ascii=False, separators=(",", ":"))


SERIES = ("query series($id: String!) { series(id: $id) { images { backdrop16x9 { source colors { muteBgColor { hex } } } }"
          " description genre ageRating { content { __typename ... on MetadataItemString { text } } } } }")
EPISODES = ("query episodes($id: String!) { season(seasonId: $id) { episodes2(input: { limit: 300 offset: 0 }) {"
            " episodeList { episode { id duration { seconds } images { screenshot16x9 { source } } } } } } }")
SEARCH = ("query search($query: String!, $limit: Int!) { search2(input: { limit: $limit q: $query searchType: { rentals: NONE } }) {"
          " posters { image { source } link { link3 { __typename to } } } } }")

heroes = set(sys.argv[2].split(",")) if len(sys.argv) > 2 else {"s-593463056c211ce4", "s-2742b08fd4f5d609", "s-6b00061dc059998b"}
for f in sorted(os.listdir(FIX)):
    if f.startswith("series__"):
        doc = load(f)
        s = doc["data"]["series"]
        extra = gql(SERIES, {"id": s["id"]})["series"]
        s.update(extra)
        save(f, doc)
        backdrop = ((s.get("images") or {}).get("backdrop16x9") or {}).get("source")
        if s["id"] in heroes:
            image(backdrop, HERO, s["id"])
    elif f.startswith("episodes__"):
        doc = load(f)
        season = doc["data"]["season"]
        sid = f[len("episodes__"):-len(".json")]
        # the fixture key lower-cases the season id "abc|s-…" to "abc_s-…"; the server wants the real id
        sid = sid.replace("_s-", "|s-", 1)
        extra = gql(EPISODES, {"id": sid})["season"]["episodes2"]["episodeList"]
        imgs = {e["episode"]["id"]: e["episode"].get("images") for e in extra}
        durs = {e["episode"]["id"]: e["episode"].get("duration") for e in extra}
        for e in season["episodes2"]["episodeList"]:
            ep = e["episode"]
            ep["images"] = imgs.get(ep["id"])
            if not ep.get("duration") and durs.get(ep["id"]):
                ep["duration"] = durs[ep["id"]]
            if ep["images"] and sid.split("|")[0] in THUMB_SEASONS:
                image(ep["images"]["screenshot16x9"]["source"], THUMB, ep["id"])
        save(f, doc)
    elif f.startswith("search__"):
        doc = load(f)
        q = f[len("search__"):-len(".json")]
        extra = gql(SEARCH, {"query": q, "limit": 60})["search2"]["posters"]
        by = {(p.get("link") or {}).get("link3", {}).get("to"): p.get("image") for p in extra}
        shown = 0
        for p in doc["data"]["search2"]["posters"]:
            to = (p.get("link") or {}).get("link3", {}).get("to")
            p["image"] = by.get(to)
            if p["image"] and q in ("bonde", "film") and shown < 14:
                shown += 1
                image(p["image"]["source"], ROW, to)
        save(f, doc)

with open(index_file, "w") as f:
    json.dump(index, f, indent=1, sort_keys=True)
