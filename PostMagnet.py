import csv
import requests 

filename = "download - 7+.list"

fields = [] 
rows = []

with open(filename, 'r') as csvfile:
    
    csvreader = csv.reader(csvfile,delimiter = ' ')
    for row in csvreader: 
        rows.append(row) 

    print("Total no. of rows: %d"%(csvreader.line_num))

API_ENDPOINT = "https://api.alldebrid.com/v4/magnet/upload?agent=AllDebrid&apikey=0BVAvZCRrGNfBIUHoLKQ"
for row in rows[:13]:
    if row != None:
        data = {'magnets':row[0]}
        r = requests.post(url = API_ENDPOINT, data = data)
        alldebrid_url = r.text 
        print("The alldebrid URL is:%s"%alldebrid_url) 
