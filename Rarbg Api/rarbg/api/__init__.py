from flask import jsonify,request,Flask
from rarbgapi import RarbgAPI

app = Flask(__name__)
app.config["DEBUG"] = False


@app.route('/', methods=['GET'])
def home():
    imdbId = request.args.get('imdbId', 'tt4154796')
    client = RarbgAPI()
    magnetList = list()
    for torrent in client.search(search_imdb=imdbId,format_='json_extended'):
        torrent_json = {}
        torrent_json['title']=torrent.filename
        torrent_json['category']=torrent.category
        torrent_json['magnet']=torrent.download
        torrent_json['size']=str(torrent.size)
        torrent_json['pubdate']=str(torrent.pubdate)
        magnetList.append(torrent_json)
    for torrent in client.search(search_imdb=imdbId,format_='json_extended',category=RarbgAPI.CATEGORY_MOVIE_H265_4K_HDR):
        torrent_json = {}
        torrent_json['title']=torrent.filename
        torrent_json['category']=torrent.category
        torrent_json['magnet']=torrent.download
        torrent_json['size']=str(torrent.size)
        torrent_json['pubdate']=str(torrent.pubdate)
        magnetList.append(torrent_json)
    for torrent in client.search(search_imdb=imdbId,format_='json_extended',category=54):
        torrent_json = {}
        torrent_json['title']=torrent.filename
        torrent_json['category']=torrent.category
        torrent_json['magnet']=torrent.download
        torrent_json['size']=str(torrent.size)
        torrent_json['pubdate']=str(torrent.pubdate)
        magnetList.append(torrent_json)
    
    return jsonify(magnetList)

app.run(host='0.0.0.0')