# Smart University API (Spring Boot + MongoDB)

## Requirements
- Java 21
- Maven 3.9+ (or IntelliJ Maven integration)
- MongoDB Community running locally on port 27017

## Run in IntelliJ
1. Open this folder as a Maven project.
2. Configure a Java 21 SDK.
3. Start MongoDB.
4. Run `SmartUniversityApplication`. The API listens on `http://localhost:8080`.

## Demo administrator
- Email: `admin@smartuniversity.tn`
- Password: `Admin123!`
Change this password and JWT secret before deployment. Admin accounts cannot self-register.

## API
- `POST /api/auth/register` student/professor registration
- `POST /api/auth/login` email/password login
- `GET /api/auth/me` current account
- `GET/POST /api/{courses|events|clubs|document-requests|sessions|attendance|grades|announcements|study-spaces|comments|enrollments}`
- `PUT /api/{resource}/{id}` update; `DELETE /api/{resource}/{id}` delete
- `PATCH /api/document-requests/{id}/status` admin status action
- `POST /api/assistant/ask` resource-grounded assistant response

All authenticated endpoints require `Authorization: Bearer <token>`. The generic resource API stores flexible documents in MongoDB collections. Restrict access further to your institution's actual policy before production.

## Frontend
Set `VITE_API_URL=http://localhost:8080/api` in the React project `.env`. Run `npm install` then `npm run dev`.

## Notes
The assistant currently performs lightweight keyword retrieval over the `course-materials` MongoDB collection. It is a working retrieval baseline, not a full vector-embedding/LLM RAG implementation. For true semantic RAG, add document extraction, chunking, embeddings, a vector store (e.g. ChromaDB), and an LLM provider.
