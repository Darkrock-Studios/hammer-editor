# Microsoft's Open XML SDK schema validation, via mikeebowen/OOXML-Validator.
FROM mcr.microsoft.com/dotnet/sdk:8.0 AS build
ARG VALIDATOR_COMMIT=326f9071a692168952bc4cb2cd9c91412ec6dcba
WORKDIR /src
RUN git clone https://github.com/mikeebowen/OOXML-Validator.git . \
	&& git checkout "$VALIDATOR_COMMIT" \
	&& dotnet publish OOXMLValidatorCLI/OOXMLValidatorCLI.csproj --framework net8.0 -c Release -r linux-x64 \
		-p:InvariantGlobalization=true -o /out

FROM mcr.microsoft.com/dotnet/runtime-deps:8.0
COPY --from=build /out/OOXMLValidatorCLI /usr/local/bin/ooxml-validator
ENTRYPOINT ["ooxml-validator"]
