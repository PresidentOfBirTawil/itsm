| A file that may not be attached | `InvalidInputException` from `AttachmentService.check` (empty, too big, wrong kind) | Message on the incident page or at the screenshot field of the report form; nothing saved. |
| An upload bigger than the server limit | Spring's `MaxUploadSizeExceededException` | Back on the page: "That file is too big. Files can be at most 5.0 MB." |
| Any error in the REST API | `ApiExceptionHandler` | JSON *problem details* with the right status: 400 invalid, 401 no or bad key, 403 not allowed, 404 not found, 409 not possible in the current state, 429 too many requests, 500 with no internal details. |
| A webhook receiver that is down | Caught in `WebhookSender` | Nothing for the user; the delivery is retried and its error shown in the delivery log. |
| A broken rule inside the assistant | Any `ItsmException` | Said politely in the chat ("You have already reported this: INC-0012 is still open…"); the conversation stays at the same step. |
