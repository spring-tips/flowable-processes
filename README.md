# Single Task Process 

```
pid=$(curl -d'{"customerId":"232"}' -H"content-type: application/json" -X POST http://localhost:8080/insurance )
tid=$(curl http://localhost:8080/insurance/${pid}/tasks  | jq -r 'keys[]' )
curl -XPOST http://localhost:8080/insurance/$pid/tasks/$tid -d'{"message":"nihao"}'
```