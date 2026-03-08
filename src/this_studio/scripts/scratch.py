import chromadb

client = chromadb.HttpClient(host="localhost", port=8000)

# List all collections
print(client.list_collections())

# Inspect yours
col = client.get_collection("socratic_tutor_collection")
print(col.count())  # how many vectors are loaded

# Peek at actual documents
print(col.peek(5))