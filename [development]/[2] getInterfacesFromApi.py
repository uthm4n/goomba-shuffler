import pandas as pd
import json
import requests
import urllib3
from IPython.display import display, HTML
from IPython.display import display_html

# Disable SSL warnings (Note: This is not recommended for production use)
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

APPLIANCE_URL = morpheus['morpheus']['applianceUrl']
API_TOKEN = morpheus['morpheus']['apiAccessToken']
INSTANCE_ID = morpheus['instance']['id']

def getInstanceApi():
    """
    Fetches instance data from the API.

    Returns:
    dict: JSON response from the API
    """
    path = f"/api/instances/{INSTANCE_ID}"
    url = f"{APPLIANCE_URL}{path}"
    headers = {"Authorization": f"Bearer {API_TOKEN}"}
    
    response = requests.get(url, headers=headers, verify=False)
    
    API_RESPONSE = json.loads(response.text)
    return API_RESPONSE

def buildDataFrame(API_RESPONSE):
    """
    Builds a pandas DataFrame from the API response.

    Args:
    API_RESPONSE (dict): JSON response from the API

    Returns:
    pandas.DataFrame or str: DataFrame containing interface information or error message
    """
    if API_RESPONSE:
        instance_id = API_RESPONSE['instance']['id']
        server_ids = API_RESPONSE['instance']['servers']
        instance_interfaces = API_RESPONSE['instance']['interfaces']
        container_interfaces = API_RESPONSE['instance']['containerDetails'][0]['server']['interfaces']
        
        rows = []
        
        for instance_interface in instance_interfaces:
            # Fix to match based on the JSON structure
            matching_container = next(
                (ci for ci in container_interfaces if str(ci['id']) == str(instance_interface.get('id'))),
                None
            )
            
            row = {
                'row': instance_interface.get('row'),
                'instance-id': instance_id,
                'server-ids': server_ids,
                'interface-id': instance_interface.get('id'),
                'name': instance_interface['network'].get('name') if instance_interface.get('network') else None,
                'public-ip-address': matching_container.get('publicIpAddress') if matching_container else None,
                'primary-interface': matching_container.get('primaryInterface') if matching_container else False,
                'active': matching_container.get('active') if matching_container else None
            }
            rows.append(row)
        
        df = pd.DataFrame(rows).set_index('row')
        return df
    else:
        return "Error building interface DataFrame"

def convert_dataframe_to_json(df):
    """
    Converts a pandas DataFrame to a JSON object.

    Args:
    df (pandas.DataFrame): DataFrame to convert

    Returns:
    str: JSON object as a string
    """
    return df.to_json(orient='index')

def display_colored_dataframe(df):
    """
    Displays a DataFrame with colored output for better readability.

    Args:
    df (pandas.DataFrame): DataFrame to display
    """
    styled_df = df.style.set_table_styles(
        [{'selector': 'thead th', 'props': [('background-color', '#f7f7f9'), ('color', '#333'), ('border', '1px solid #ccc')]},
         {'selector': 'tbody tr:nth-child(even)', 'props': [('background-color', '#f9f9f9')]},
         {'selector': 'tbody tr:nth-child(odd)', 'props': [('background-color', '#fff')]},
         {'selector': 'tbody td', 'props': [('border', '1px solid #ccc')]}]
    ).set_properties(**{
        'text-align': 'left',
        'padding': '5px'
    }).map(lambda x: 'color: red' if x is None else 'color: black')
    
    rawHTML = display_html(styled_df._render(sparse_index=['row'], sparse_columns=['instance-id', 'server-ids', 'interface-id', 'name', 'public-ip-address', 'primary-interface', 'active']), raw=True)
    renderedHTML = display(HTML(styled_df._render(sparse_index=['row'], sparse_columns=['instance-id', 'server-ids', 'interface-id', 'name', 'public-ip-address', 'primary-interface', 'active'])))
    return renderedHTML

def main():
    """
    Main function to fetch API response and build DataFrame.
    """
    apiResponse = getInstanceApi()
    interfaces = buildDataFrame(apiResponse)
   # display_colored_dataframe(interfaces)
    json_output = convert_dataframe_to_json(interfaces)
    print(json_output)

if __name__ == "__main__":
    main()
